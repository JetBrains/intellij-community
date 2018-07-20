// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInsight.daemon.impl.analysis.RemoveAttributeIntentionFix;
import com.intellij.codeInsight.daemon.impl.analysis.RemoveTagIntentionFix;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.reference.SoftReference;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyAttributeDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyTagDescriptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.Reference;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxRedundantPropertyValueInspection extends XmlSuppressableInspectionTool {
  private static final Logger LOG = Logger.getInstance(JavaFxRedundantPropertyValueInspection.class);

  private static Reference<Map<String, Map<String, String>>> ourDefaultPropertyValues;

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (!JavaFxFileTypeFactory.isFxml(session.getFile())) return PsiElementVisitor.EMPTY_VISITOR;

    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttribute(XmlAttribute attribute) {
        super.visitXmlAttribute(attribute);
        final XmlAttributeDescriptor descriptor = attribute.getDescriptor();
        if (!(descriptor instanceof JavaFxPropertyAttributeDescriptor)) return;
        final String attributeName = attribute.getName();
        final String attributeValue = attribute.getValue();
        if (attributeValue == null ||
            attributeValue.startsWith("$") ||
            attributeValue.startsWith("#") ||
            attributeValue.startsWith("%") ||
            FxmlConstants.FX_ID.equals(attributeName) ||
            FxmlConstants.FX_VALUE.equals(attributeName) ||
            FxmlConstants.FX_CONSTANT.equals(attributeName) ||
            FxmlConstants.FX_CONTROLLER.equals(attributeName)) {
          return;
        }
        final PsiClass tagClass = JavaFxPsiUtil.getTagClass(attribute.getParent());
        final String defaultValue = getDefaultValue(attributeName, tagClass);
        if (defaultValue == null) return;

        if (isEqualValue(tagClass, attributeValue, defaultValue, descriptor.getDeclaration())) {
          holder.registerProblem(attribute, "Attribute is redundant because it contains default value",
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 new RemoveAttributeIntentionFix(attributeName));
        }
      }

      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        final XmlElementDescriptor descriptor = tag.getDescriptor();
        if (!(descriptor instanceof JavaFxPropertyTagDescriptor)) {
          return;
        }
        if (tag.getSubTags().length != 0) return;
        final String tagText = tag.getValue().getTrimmedText();
        if (tagText.startsWith("$") ||
            tagText.startsWith("#") ||
            tagText.startsWith("%")) {
          return;
        }

        final XmlTag parentTag = tag.getParentTag();
        if (parentTag == null) return;
        final PsiClass tagClass = JavaFxPsiUtil.getTagClass(parentTag);
        final String defaultValue = getDefaultValue(tag.getName(), tagClass);
        if (defaultValue == null) return;

        if (isEqualValue(tagClass, tagText, defaultValue, descriptor.getDeclaration())) {
          holder.registerProblem(tag, "Tag is redundant because it contains default value",
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 new RemoveTagIntentionFix(tag.getName(), tag));
        }
      }
    };
  }

  @Nullable
  private static String getDefaultValue(@NotNull String propertyName, @Nullable PsiClass containingClass) {
    for (PsiClass psiClass = containingClass; psiClass != null; psiClass = psiClass.getSuperClass()) {
      final String qualifiedName = psiClass.getQualifiedName();
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName)) break;
      final String defaultValue = getDefaultPropertyValue(qualifiedName, propertyName);
      if (defaultValue != null) {
        return defaultValue;
      }
    }
    return null;
  }

  private static boolean isEqualValue(@Nullable PsiClass containingClass,
                                      @NotNull String attributeValue,
                                      @NotNull String defaultValue,
                                      @Nullable PsiElement declaration) {
    if (!(declaration instanceof PsiMember)) return false;
    final String boxedQName = JavaFxPsiUtil.getBoxedPropertyType(containingClass, (PsiMember)declaration);
    if (boxedQName == null) {
      return defaultValue.equals(attributeValue);
    }
    try {
      switch (boxedQName) {
        case CommonClassNames.JAVA_LANG_BOOLEAN:
          return Boolean.parseBoolean(defaultValue) == Boolean.parseBoolean(attributeValue);
        case CommonClassNames.JAVA_LANG_DOUBLE:
          return Double.compare(Double.parseDouble(defaultValue), Double.parseDouble(attributeValue)) == 0;
        case CommonClassNames.JAVA_LANG_FLOAT:
          return Float.compare(Float.parseFloat(defaultValue), Float.parseFloat(attributeValue)) == 0;
        case CommonClassNames.JAVA_LANG_INTEGER:
          return Integer.parseInt(defaultValue) == Integer.parseInt(attributeValue);
        case CommonClassNames.JAVA_LANG_LONG:
          return Long.parseLong(defaultValue) == Long.parseLong(attributeValue);
        case CommonClassNames.JAVA_LANG_SHORT:
          return Short.parseShort(defaultValue) == Short.parseShort(attributeValue);
        case CommonClassNames.JAVA_LANG_BYTE:
          return Byte.parseByte(defaultValue) == Byte.parseByte(attributeValue);
        default:
          return defaultValue.equals(attributeValue);
      }
    }
    catch (NumberFormatException ignored) {
      return false;
    }
  }

  @Nullable
  private static String getDefaultPropertyValue(String classQualifiedName, String propertyName) {
    final Map<String, String> values = getDefaultPropertyValues(classQualifiedName);
    return values != null ? values.get(propertyName) : null;
  }

  /**
   * Load property values resource. The resource is produced with the script JavaFxGenerateDefaultPropertyValuesScript (can be found in tests)
   */
  @Nullable
  private static Map<String, String> getDefaultPropertyValues(String classQualifiedName) {
    Map<String, Map<String, String>> values = SoftReference.dereference(ourDefaultPropertyValues);
    if (values == null) {
      values = loadDefaultPropertyValues(JavaFxRedundantPropertyValueInspection.class.getSimpleName() + "8.txt");
      ourDefaultPropertyValues = new SoftReference<>(values);
    }
    return values.get(classQualifiedName);
  }

  /**
   * The file format is {@code ClassName#propertyName:type=value} per line, line with leading double dash (--) is commented out
   */
  @NotNull
  private static Map<String, Map<String, String>> loadDefaultPropertyValues(@NotNull String resourceName) {
    final URL resource = JavaFxRedundantPropertyValueInspection.class.getResource(resourceName);
    if (resource == null) {
      LOG.warn("Resource not found: " + resourceName);
      return Collections.emptyMap();
    }

    final Map<String, Map<String, String>> result = new THashMap<>(200);
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(), CharsetToolkit.UTF8_CHARSET))) {
      for (String line : FileUtil.loadLines(reader)) {
        if (line.isEmpty() || line.startsWith("--")) continue;
        boolean lineParsed = false;
        final int p1 = line.indexOf('#');
        if (p1 > 0 && p1 < line.length()) {
          final String className = line.substring(0, p1);
          final int p2 = line.indexOf('=', p1);
          if (p2 > p1 && p2 < line.length()) {
            final String propertyName = line.substring(p1 + 1, p2);
            final String valueText = line.substring(p2 + 1);
            lineParsed = true;
            final Map<String, String> properties = result.computeIfAbsent(className, ignored -> new THashMap<>());
            if (properties.put(propertyName, valueText) != null) {
              LOG.warn("Duplicate default property value " + line);
            }
          }
        }
        if (!lineParsed) {
          LOG.warn("Can't parse default property value " + line);
        }
      }
    }
    catch (IOException e) {
      LOG.warn("Can't read resource: " + resourceName, e);
      return Collections.emptyMap();
    }
    return result;
  }
}

