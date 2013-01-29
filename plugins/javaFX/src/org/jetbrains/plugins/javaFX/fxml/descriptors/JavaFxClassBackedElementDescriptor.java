package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.Validator;
import com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.xml.XmlAttributeImpl;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: anna
 * Date: 1/9/13
 */
public class JavaFxClassBackedElementDescriptor implements XmlElementDescriptor, Validator<XmlTag> {
  private final PsiClass myPsiClass;
  private final String myName;

  public JavaFxClassBackedElementDescriptor(String name, XmlTag tag) {
    this(name, findPsiClass(name, tag));
  }

  public static PsiClass findPsiClass(String name, XmlTag tag) {
    return findPsiClass(name, JavaFxPsiUtil.parseImports((XmlFile)tag.getContainingFile()), tag, tag.getProject());
  }

  public JavaFxClassBackedElementDescriptor(String name, PsiClass aClass) {
    myName = name;
    myPsiClass = aClass;
  }

  private static PsiClass findPsiClass(String name, List<String> imports, XmlTag tag, Project project) {
    PsiClass psiClass = null;
    if (imports != null) {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);

      PsiFile file = tag.getContainingFile();
      for (String anImport : imports) {
        if (StringUtil.endsWith(anImport, "." + name)) {
          psiClass = psiFacade.findClass(anImport, file.getResolveScope()); 
        } else if (StringUtil.endsWith(anImport, ".*")) {
          psiClass = psiFacade.findClass(StringUtil.trimEnd(anImport, "*") + name, file.getResolveScope());
        }
        if (psiClass != null) {
          return psiClass;
        }
      }
    }
    return psiClass;
  }

  @Override
  public String getQualifiedName() {
    return myPsiClass != null ? myPsiClass.getQualifiedName() : getName();
  }

  @Override
  public String getDefaultName() {
    return getName();
  }

  @Override
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    if (context != null) {
      if (myPsiClass != null) {
        final List<XmlElementDescriptor> children = new ArrayList<XmlElementDescriptor>();
        collectProperties(children, true, new Function<PsiField, XmlElementDescriptor>() {
          @Override
          public XmlElementDescriptor fun(PsiField field) {
            return new JavaFxPropertyElementDescriptor(myPsiClass, field.getName(), false);
          }
        });

        collectParentStaticProperties(context, children, new Function<PsiMethod, XmlElementDescriptor>() {
          @Override
          public XmlElementDescriptor fun(PsiMethod method) {
            final PsiClass aClass = method.getContainingClass();
            return new JavaFxPropertyElementDescriptor(aClass, PropertyUtil.getPropertyName(method.getName()), true);
          }
        });

        final PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(myPsiClass, Collections.singleton(JavaFxCommonClassNames.JAVAFX_BEANS_DEFAULT_PROPERTY));
        if (annotation != null) {
          final PsiAnnotationMemberValue memberValue = annotation.findAttributeValue(null);
          if (memberValue != null) {
            final String propertyName = StringUtil.stripQuotesAroundValue(memberValue.getText());
            final PsiMethod getter = findPropertyGetter(propertyName, myPsiClass);
            if (getter != null) {
              final PsiType returnType = getter.getReturnType();
              children.addAll(JavaFxPropertyElementDescriptor.collectDescriptorsByCollection(returnType, myPsiClass.getResolveScope()));
            }
          }
        }

        for (String name : FxmlConstants.FX_DEFAULT_ELEMENTS) {
          children.add(new JavaFxDefaultPropertyElementDescriptor(name, null));
        }

        if (!children.isEmpty()) {
          return children.toArray(new XmlElementDescriptor[children.size()]);
        }
      }
    }
    return XmlElementDescriptor.EMPTY_ARRAY;
  }

  private static <T> void collectParentStaticProperties(XmlTag context, List<T> children, Function<PsiMethod, T> factory) {
    XmlTag tag = context;
    while (tag != null) {
      final XmlElementDescriptor descr = tag.getDescriptor();
      if (descr instanceof JavaFxClassBackedElementDescriptor) {
        final PsiElement element = descr.getDeclaration();
        if (element instanceof PsiClass) {
          for (PsiMethod method : ((PsiClass)element).getMethods()) {
            if (method.hasModifierProperty(PsiModifier.STATIC) && method.getName().startsWith("set")) {
              final PsiParameter[] parameters = method.getParameterList().getParameters();
              if (parameters.length == 2 && InheritanceUtil.isInheritor(parameters[0].getType(), JavaFxCommonClassNames.JAVAFX_SCENE_NODE)) {
                children.add(factory.fun(method));
              }
            }
          }
        }
      }
      tag = tag.getParentTag();
    }
  }

  @Nullable
  @Override
  public XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag) {
    final String name = childTag.getName();
    if (isClassTag(name)) {
      return new JavaFxClassBackedElementDescriptor(name, childTag);
    }
    else {
      final String shortName = StringUtil.getShortName(name);
      if (!name.equals(shortName)) { //static property
        final PsiMethod propertySetter = findPropertySetter(name, childTag);
        if (propertySetter != null) {
          return new JavaFxPropertyElementDescriptor(propertySetter.getContainingClass(), shortName, true);
        }
        return null;
      }
      if (FxmlConstants.FX_DEFAULT_ELEMENTS.contains(name)) {
        return new JavaFxDefaultPropertyElementDescriptor(name, childTag);
      }
      return myPsiClass != null ? new JavaFxPropertyElementDescriptor(myPsiClass, name, false) : null;
    }
  }

  public static boolean isClassTag(String name) {
    final String shortName = StringUtil.getShortName(name);
    return StringUtil.isCapitalized(name) && name.equals(shortName);
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(@Nullable XmlTag context) {
    //todo filter
    if (context != null) {
      final String name = context.getName();
      if (Comparing.equal(name, getName()) && myPsiClass != null) {
        final List<XmlAttributeDescriptor> simpleAttrs = new ArrayList<XmlAttributeDescriptor>();
        collectProperties(simpleAttrs, false, new Function<PsiField, XmlAttributeDescriptor>() {
          @Override
          public XmlAttributeDescriptor fun(PsiField field) {
            return new JavaFxPropertyAttributeDescriptor(field.getName(), myPsiClass);
          }
        });
        collectParentStaticProperties(context.getParentTag(), simpleAttrs, new Function<PsiMethod, XmlAttributeDescriptor>() {
          @Override
          public XmlAttributeDescriptor fun(PsiMethod method) {
            return new JavaFxSetterAttributeDescriptor(method, method.getContainingClass());
          }
        });
        for (String defaultProperty : FxmlConstants.FX_DEFAULT_PROPERTIES) {
          simpleAttrs.add(new JavaFxDefaultAttributeDescriptor(defaultProperty));
        }
        return simpleAttrs.isEmpty() ? XmlAttributeDescriptor.EMPTY : simpleAttrs.toArray(new XmlAttributeDescriptor[simpleAttrs.size()]);
      }
    }
    return XmlAttributeDescriptor.EMPTY;
  }

  private <T> void collectProperties(List<T> children, boolean includeListProperties, Function<PsiField, T> factory) {
    final PsiField[] fields = myPsiClass.getAllFields();
    if (fields.length > 0) {
      for (PsiField field : fields) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
        final PsiType fieldType = field.getType();
        if (PropertyUtil.findPropertyGetter(myPsiClass, field.getName(), false, true) != null && 
            InheritanceUtil.isInheritor(fieldType, JavaFxCommonClassNames.JAVAFX_BEANS_PROPERTY_PROPERTY) || 
            fieldType.equalsToText(CommonClassNames.JAVA_LANG_STRING) ||
            includeListProperties && GenericsHighlightUtil.getCollectionItemType(field.getType(), myPsiClass.getResolveScope()) != null) {
          children.add(factory.fun(field));
        }
      }
    }
  }

  @Nullable
  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(@NonNls String attributeName, @Nullable XmlTag context) {
    if (myPsiClass == null) return null;
    if (myPsiClass.findFieldByName(attributeName, true) == null) {
      if (FxmlConstants.FX_DEFAULT_PROPERTIES.contains(attributeName)){
        return new JavaFxDefaultAttributeDescriptor(attributeName);
      } else {
        final PsiMethod propertySetter = findPropertySetter(attributeName, context);
        if (propertySetter != null) {
          return new JavaFxStaticPropertyAttributeDescriptor(propertySetter, attributeName);
        }
        return null;
      }
    }
    return new JavaFxPropertyAttributeDescriptor(attributeName, myPsiClass);
  }

  @Nullable
  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    return getAttributeDescriptor(attribute.getName(), attribute.getParent());
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return null;
  }

  @Nullable
  @Override
  public XmlElementsGroup getTopGroup() {
    return null;
  }

  @Override
  public int getContentType() {
    return CONTENT_TYPE_UNKNOWN;
  }

  @Nullable
  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public PsiElement getDeclaration() {
    return myPsiClass;
  }

  @Override
  public String getName(PsiElement context) {
    return getName();
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void init(PsiElement element) {
  }

  @Override
  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public void validate(@NotNull XmlTag context, @NotNull ValidationHost host) {
    final XmlTag parentTag = context.getParentTag();
    if (parentTag != null) {
      final XmlAttribute attribute = context.getAttribute(FxmlConstants.FX_CONTROLLER);
      if (attribute != null) {
        host.addMessage(((XmlAttributeImpl)attribute).getNameElement(), "fx:controller can only be applied to root element", ValidationHost.ErrorType.ERROR); //todo add delete/move to upper tag fix
      }
    }
    validateTagAccordingToFieldType(context, parentTag, host);
  }

  private void validateTagAccordingToFieldType(XmlTag context, XmlTag parentTag, ValidationHost host) {
    if (myPsiClass != null && myPsiClass.isValid()) {
      final XmlElementDescriptor descriptor = parentTag != null ? parentTag.getDescriptor() : null;
      if (descriptor instanceof JavaFxPropertyElementDescriptor) {
        final PsiElement declaration = descriptor.getDeclaration();
        if (declaration instanceof PsiField) {
          final PsiType type = ((PsiField)declaration).getType();
          final PsiType collectionItemType = GenericsHighlightUtil.getCollectionItemType(type, myPsiClass.getResolveScope());
          if (collectionItemType != null && PsiPrimitiveType.getUnboxedType(collectionItemType) == null) {
            final PsiClass baseClass = PsiUtil.resolveClassInType(collectionItemType);
            if (baseClass != null) {
              final String qualifiedName = baseClass.getQualifiedName();
              if (qualifiedName != null && !Comparing.strEqual(qualifiedName, CommonClassNames.JAVA_LANG_STRING)) {
                if (!InheritanceUtil.isInheritor(myPsiClass, qualifiedName)) {
                  host.addMessage(context.getNavigationElement(), 
                                  "Unable to coerce " + HighlightUtil.formatClass(myPsiClass)+ " to " + qualifiedName, ValidationHost.ErrorType.ERROR);
                }
              }
            }
          }
        }
      }
    }
  }

  public static PsiMethod findPropertySetter(String attributeName, XmlTag context) {
    final String packageName = StringUtil.getPackageName(attributeName);
    if (context != null && !StringUtil.isEmptyOrSpaces(packageName)) {
      final PsiClass classWithStaticProperty = findPsiClass(packageName, context);
      if (classWithStaticProperty != null) {
        return findPropertySetter(attributeName, classWithStaticProperty);
      }
    }
    return null;
  }

  public static PsiMethod findPropertySetter(String attributeName, PsiClass classWithStaticProperty) {
    final String setterName = PropertyUtil.suggestSetterName(StringUtil.getShortName(attributeName));
    final PsiMethod[] setters = classWithStaticProperty.findMethodsByName(setterName, true);
    if (setters.length == 1) {
      return setters[0];
    }
    return null;
  }

  public static PsiMethod findPropertyGetter(String attributeName, PsiClass classWithStaticProperty) {
    final String getterName = PropertyUtil.suggestGetterName(StringUtil.getShortName(attributeName), null);
    final PsiMethod[] getters = classWithStaticProperty.findMethodsByName(getterName, true);
    if (getters.length >= 1) {
      return getters[0];
    }
    return null;
  }
}
