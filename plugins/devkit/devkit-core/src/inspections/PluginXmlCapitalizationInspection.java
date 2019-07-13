// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixBase;
import com.intellij.codeInspection.NlsCapitalizationUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.properties.ResourceBundleReference;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Action;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class PluginXmlCapitalizationInspection extends BasicDomElementsInspection<IdeaPlugin> {

  public PluginXmlCapitalizationInspection() {
    super(IdeaPlugin.class);
  }

  @Override
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    if (element instanceof ActionOrGroup) {
      checkActionOrGroup((ActionOrGroup)element, holder);
    }
    else if (element instanceof Extension) {
      checkExtension((Extension)element, holder);
    }
  }

  private static void checkActionOrGroup(ActionOrGroup actionOrGroup, DomElementAnnotationHolder holder) {
    checkActionOrGroupCapitalization(holder, actionOrGroup, ActionOrGroupText.TEXT);
    checkActionOrGroupCapitalization(holder, actionOrGroup, ActionOrGroupText.DESCRIPTION);
  }

  enum ActionOrGroupText {

    TEXT(ActionOrGroup::getText, Nls.Capitalization.Title, ".text", actionOrGroup -> {
      if (!(actionOrGroup instanceof Action)) return false;
      final PsiClass actionClass = ((Action)actionOrGroup).getClazz().getValue();
      return actionClass == null || actionClass.getConstructors().length == 0;
    }),

    DESCRIPTION(ActionOrGroup::getDescription, Nls.Capitalization.Sentence, ".description", actionOrGroup -> false);

    private final Function<ActionOrGroup, GenericDomValue> myGetter;
    private final Nls.Capitalization myCapitalization;
    private final String mySuffix;
    private final Function<ActionOrGroup, Boolean> myRequired;

    ActionOrGroupText(Function<ActionOrGroup, GenericDomValue> getter,
                      Nls.Capitalization capitalization, String propertyKeySuffix,
                      Function<ActionOrGroup, Boolean> required) {
      myGetter = getter;
      myCapitalization = capitalization;
      mySuffix = propertyKeySuffix;
      myRequired = required;
    }
  }

  private static void checkActionOrGroupCapitalization(DomElementAnnotationHolder holder,
                                                       ActionOrGroup actionOrGroup,
                                                       ActionOrGroupText actionOrGroupText) {
    final GenericDomValue genericDomValue = actionOrGroupText.myGetter.apply(actionOrGroup);
    final Nls.Capitalization capitalization = actionOrGroupText.myCapitalization;
    if (checkCapitalization(holder, genericDomValue, capitalization)) return;

    final IdeaPlugin ideaPlugin = DomUtil.getParentOfType(actionOrGroup, IdeaPlugin.class, true);
    if (ideaPlugin == null) return;

    final XmlElement resourceBundleTag = ideaPlugin.getResourceBundle().getXmlElement();
    if (resourceBundleTag == null) return;

    final ResourceBundleReference bundleReference =
      ContainerUtil.findInstance(resourceBundleTag.getReferences(), ResourceBundleReference.class);
    if (bundleReference == null) return;

    final PropertiesFileImpl bundleFile = ObjectUtils.tryCast(bundleReference.resolve(), PropertiesFileImpl.class);
    if (bundleFile == null) return;

    final String resourceKey = "action." + actionOrGroup.getId().getStringValue() + actionOrGroupText.mySuffix;
    final Property property = ObjectUtils.tryCast(bundleFile.findPropertyByKey(resourceKey), Property.class);
    if (property == null) {
      if (actionOrGroupText.myRequired.apply(actionOrGroup)) {
        holder.createProblem(actionOrGroup, "Missing resource bundle key '" + resourceKey + "'");
      }
    }
    else {
      highlightCapitalization(holder, actionOrGroup, property.getValue(), capitalization, property);
    }
  }

  private static final Set<String> EXTENSION_KNOWN_NON_NLS_ATTRIBUTES = ContainerUtil.immutableSet(
    Extension.ID_ATTRIBUTE, Extension.ORDER_ATTRIBUTE, Extension.OS_ATTRIBUTE
  );

  private static void checkExtension(Extension extension, DomElementAnnotationHolder holder) {
    for (DomChildrenDescription child : extension.getGenericInfo().getFixedChildrenDescriptions()) {
      for (DomElement value : child.getValues(extension)) {
        if (!value.exists() || !(value instanceof GenericDomValue)) continue;

        GenericDomValue genericDomValue = (GenericDomValue)value;
        checkDomValue(extension, holder, child, genericDomValue);
      }
    }

    final List<? extends DomAttributeChildDescription> attributes = extension.getGenericInfo().getAttributeChildrenDescriptions();
    if (attributes.size() == EXTENSION_KNOWN_NON_NLS_ATTRIBUTES.size() + 1) {
      return;
    }

    for (DomAttributeChildDescription attributeDescription : attributes) {
      final String attributeName = attributeDescription.getXmlElementName();
      if (EXTENSION_KNOWN_NON_NLS_ATTRIBUTES.contains(attributeName) ||
          Extension.isClassField(attributeName)) {
        continue;
      }


      final GenericAttributeValue attributeValue = attributeDescription.getDomAttributeValue(extension);
      if (!DomUtil.hasXml(attributeValue)) continue;

      checkDomValue(extension, holder, attributeDescription, attributeValue);
    }
  }

  private static void checkDomValue(Extension extension,
                                    DomElementAnnotationHolder holder,
                                    DomChildrenDescription childrenDescription,
                                    GenericDomValue genericDomValue) {
    final Class attributeType = DomUtil.getGenericValueParameter(childrenDescription.getType());
    if (attributeType != String.class) return;

    final PsiElement declaration = childrenDescription.getDeclaration(extension.getManager().getProject());
    if (declaration instanceof PsiField) {
      final Nls.Capitalization capitalization = NlsCapitalizationUtil.getCapitalizationFromAnno((PsiModifierListOwner)declaration);
      if (capitalization == Nls.Capitalization.NotSpecified) return;

      checkCapitalizationWithKey(holder, genericDomValue, capitalization);
    }
  }

  private static void checkCapitalizationWithKey(DomElementAnnotationHolder holder,
                                                 GenericDomValue genericDomValue,
                                                 Nls.Capitalization capitalization) {
    if (!DomUtil.hasXml(genericDomValue)) return;

    final XmlElement xmlElement = DomUtil.getValueElement(genericDomValue);
    if (xmlElement == null) return;

    for (PsiReference reference : xmlElement.getReferences()) {
      if (reference instanceof PropertyReference) {
        ResolveResult[] resolveResults = ((PropertyReference)reference).multiResolve(false);
        if (resolveResults.length == 1 && resolveResults[0].isValidResult()) {
          PsiElement element = resolveResults[0].getElement();
          if (element instanceof Property) {
            final Property property = (Property)element;
            String value = property.getValue();
            highlightCapitalization(holder, genericDomValue, value, capitalization, property);
            return;
          }
        }
        return;
      }
    }

    checkCapitalization(holder, genericDomValue, capitalization);
  }

  private static boolean checkCapitalization(DomElementAnnotationHolder holder,
                                             GenericDomValue genericDomValue,
                                             Nls.Capitalization capitalization) {
    if (!DomUtil.hasXml(genericDomValue)) return false;

    final String stringValue = genericDomValue.getStringValue();
    highlightCapitalization(holder, genericDomValue, stringValue, capitalization, null);
    return true;
  }

  private static void highlightCapitalization(DomElementAnnotationHolder holder,
                                              DomElement domElement,
                                              String value,
                                              Nls.Capitalization capitalization,
                                              @Nullable Property property) {
    if (StringUtil.isEmptyOrSpaces(value)) return;

    final String escapedValue = XmlUtil.unescape(value).replace("_", "");
    if (NlsCapitalizationUtil.isCapitalizationSatisfied(escapedValue, capitalization)) {
      return;
    }

    final LocalQuickFix quickFix = new LocalQuickFixBase("Properly capitalize '" + escapedValue + '\'', "Properly capitalize") {

      @Override
      public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
        return property != null ? property : currentFile;
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        if (property != null) {
          property.setValue(NlsCapitalizationUtil.fixValue(value, capitalization));
        }
        else {
          assert domElement instanceof GenericDomValue : domElement;
          ((GenericDomValue)domElement).setStringValue(NlsCapitalizationUtil.fixValue(value, capitalization));
        }
      }
    };


    holder.createProblem(domElement,
                         "String '" + escapedValue + "' is not properly capitalized. " +
                         "It should have " + StringUtil.toLowerCase(capitalization.toString()) + " capitalization",
                         quickFix);
  }
}
