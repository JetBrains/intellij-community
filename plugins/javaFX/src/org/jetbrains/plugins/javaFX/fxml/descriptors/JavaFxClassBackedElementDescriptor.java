package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.xml.XmlAttributeImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
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
import java.util.List;

/**
 * User: anna
 * Date: 1/9/13
 */
public class JavaFxClassBackedElementDescriptor implements XmlElementDescriptor, Validator<XmlTag> {
  private final PsiClass myPsiClass;
  private final String myName;

  public JavaFxClassBackedElementDescriptor(String name, XmlTag tag) {
    this(name, JavaFxPsiUtil.findPsiClass(name, tag));
  }

  public JavaFxClassBackedElementDescriptor(String name, PsiClass aClass) {
    myName = name;
    myPsiClass = aClass;
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
        collectProperties(children, context, new Function<PsiField, XmlElementDescriptor>() {
          @Override
          public XmlElementDescriptor fun(PsiField field) {
            return new JavaFxPropertyElementDescriptor(myPsiClass, field.getName(), false);
          }
        });

        collectStaticElementDescriptors(context, children);

        final PsiType returnType = JavaFxPsiUtil.getDefaultPropertyExpectedType(myPsiClass);
        if (returnType != null) {
          JavaFxPropertyElementDescriptor.collectDescriptorsByCollection(returnType, myPsiClass.getResolveScope(), children);
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

  static void collectStaticAttributesDescriptors(@Nullable XmlTag context, List<XmlAttributeDescriptor> simpleAttrs) {
    if (context == null) return;
    collectParentStaticProperties(context.getParentTag(), simpleAttrs, new Function<PsiMethod, XmlAttributeDescriptor>() {
      @Override
      public XmlAttributeDescriptor fun(PsiMethod method) {
        return new JavaFxSetterAttributeDescriptor(method, method.getContainingClass());
      }
    });
  }

  protected static void collectStaticElementDescriptors(XmlTag context, List<XmlElementDescriptor> children) {
    collectParentStaticProperties(context, children, new Function<PsiMethod, XmlElementDescriptor>() {
      @Override
      public XmlElementDescriptor fun(PsiMethod method) {
        final PsiClass aClass = method.getContainingClass();
        return new JavaFxPropertyElementDescriptor(aClass, PropertyUtil.getPropertyName(method.getName()), true);
      }
    });
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
    if (FxmlConstants.FX_DEFAULT_ELEMENTS.contains(name)) {
      return new JavaFxDefaultPropertyElementDescriptor(name, childTag);
    }
    final String shortName = StringUtil.getShortName(name);
    if (!name.equals(shortName)) { //static property
      final PsiMethod propertySetter = JavaFxPsiUtil.findPropertySetter(name, childTag);
      if (propertySetter != null) {
        return new JavaFxPropertyElementDescriptor(propertySetter.getContainingClass(), shortName, true);
      }

      final Project project = childTag.getProject();
      if (JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project)) == null) {
        return null;
      }
    }

    final String parentTagName = contextTag.getName();
    if (!FxmlConstants.FX_ROOT.equals(parentTagName) && !FxmlConstants.FX_DEFINE.equals(parentTagName)) {
      final JavaFxPropertyElementDescriptor elementDescriptor = new JavaFxPropertyElementDescriptor(myPsiClass, name, false);
      if (myPsiClass != null && elementDescriptor.getDeclaration() != null) {
        return elementDescriptor;
      }
    }
    return new JavaFxClassBackedElementDescriptor(name, childTag);
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(@Nullable XmlTag context) {
    //todo filter
    if (context != null) {
      final String name = context.getName();
      if (Comparing.equal(name, getName()) && myPsiClass != null) {
        final List<XmlAttributeDescriptor> simpleAttrs = new ArrayList<XmlAttributeDescriptor>();
        collectProperties(simpleAttrs, context, new Function<PsiField, XmlAttributeDescriptor>() {
          @Override
          public XmlAttributeDescriptor fun(PsiField field) {
            return new JavaFxPropertyAttributeDescriptor(field.getName(), myPsiClass);
          }
        });
        collectStaticAttributesDescriptors(context, simpleAttrs);
        for (String defaultProperty : FxmlConstants.FX_DEFAULT_PROPERTIES) {
          simpleAttrs.add(new JavaFxDefaultAttributeDescriptor(defaultProperty, myPsiClass));
        }
        return simpleAttrs.isEmpty() ? XmlAttributeDescriptor.EMPTY : simpleAttrs.toArray(new XmlAttributeDescriptor[simpleAttrs.size()]);
      }
    }
    return XmlAttributeDescriptor.EMPTY;
  }

  private <T> void collectProperties(List<T> children, XmlTag context, Function<PsiField, T> factory) {
    final PsiField[] fields = myPsiClass.getAllFields();
    if (fields.length > 0) {
      for (PsiField field : fields) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
        final PsiType fieldType = field.getType();
        if (!JavaFxPsiUtil.isReadOnly(field.getName(), context) && 
            InheritanceUtil.isInheritor(fieldType, JavaFxCommonClassNames.JAVAFX_BEANS_PROPERTY) || 
            fieldType.equalsToText(CommonClassNames.JAVA_LANG_STRING) ||
            GenericsHighlightUtil.getCollectionItemType(field.getType(), myPsiClass.getResolveScope()) != null) {
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
        return new JavaFxDefaultAttributeDescriptor(attributeName, myPsiClass);
      } else {
        final PsiMethod propertySetter = JavaFxPsiUtil.findPropertySetter(attributeName, context);
        if (propertySetter != null) {
          return new JavaFxStaticPropertyAttributeDescriptor(propertySetter, attributeName);
        }
        final PsiMethod getter = JavaFxPsiUtil.findPropertyGetter(attributeName, myPsiClass);
        if (getter != null) {
          return new JavaFxPropertyAttributeDescriptor(attributeName, myPsiClass);
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
    if (myPsiClass != null && myPsiClass.isValid()) {
      final String message = JavaFxPsiUtil.isAbleToInstantiate(myPsiClass);
      if (message != null) {
        host.addMessage(context, message, ValidationHost.ErrorType.ERROR);
      }
    }
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
}
