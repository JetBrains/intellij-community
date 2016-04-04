package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.Validator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
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
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        collectWritableProperties(children,
                                  (member) -> new JavaFxPropertyElementDescriptor(myPsiClass, PropertyUtil.getPropertyName(member), false));

        final JavaFxPropertyElementDescriptor defaultPropertyDescriptor = getDefaultPropertyDescriptor();
        if (defaultPropertyDescriptor != null) {
          Collections.addAll(children, defaultPropertyDescriptor.getElementsDescriptors(context));
        }
        else {
          for (String name : FxmlConstants.FX_DEFAULT_ELEMENTS) {
            children.add(new JavaFxDefaultPropertyElementDescriptor(name, null));
          }
        }

        collectStaticElementDescriptors(context, children);

        if (!children.isEmpty()) {
          return children.toArray(new XmlElementDescriptor[children.size()]);
        }
      }
    }
    return XmlElementDescriptor.EMPTY_ARRAY;
  }

  private JavaFxPropertyElementDescriptor getDefaultPropertyDescriptor() {
    final PsiAnnotation defaultProperty = AnnotationUtil
      .findAnnotationInHierarchy(myPsiClass, Collections.singleton(JavaFxCommonNames.JAVAFX_BEANS_DEFAULT_PROPERTY));
    if (defaultProperty != null) {
      final PsiAnnotationMemberValue defaultPropertyAttributeValue = defaultProperty.findAttributeValue("value");
      if (defaultPropertyAttributeValue instanceof PsiLiteralExpression) {
        final Object value = ((PsiLiteralExpression)defaultPropertyAttributeValue).getValue();
        if (value instanceof String) {
          return new JavaFxPropertyElementDescriptor(myPsiClass, (String)value, false);
        }
      }
    }
    return null;
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
          final List<PsiMethod> setters = CachedValuesManager.getCachedValue(element, new CachedValueProvider<List<PsiMethod>>() {
            @Nullable
            @Override
            public Result<List<PsiMethod>> compute() {
              final List<PsiMethod> meths = new ArrayList<PsiMethod>();
              for (PsiMethod method : ((PsiClass)element).getAllMethods()) {
                if (method.hasModifierProperty(PsiModifier.STATIC) && method.getName().startsWith("set")) {
                  final PsiParameter[] parameters = method.getParameterList().getParameters();
                  if (parameters.length == 2 &&
                      InheritanceUtil.isInheritor(parameters[0].getType(), JavaFxCommonNames.JAVAFX_SCENE_NODE)) {
                    meths.add(method);
                  }
                }
              }
              return Result.create(meths, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
            }
          });
          for (PsiMethod setter : setters) {
            children.add(factory.fun(setter));
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
      final PsiMethod propertySetter = JavaFxPsiUtil.findStaticPropertySetter(name, childTag);
      if (propertySetter != null) {
        return new JavaFxPropertyElementDescriptor(propertySetter.getContainingClass(), shortName, true);
      }

      final Project project = childTag.getProject();
      if (JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project)) == null) {
        return null;
      }
    }

    final String parentTagName = contextTag.getName();
    if (myPsiClass != null) {
      if (!FxmlConstants.FX_DEFINE.equals(parentTagName)) {
        if (FxmlConstants.FX_ROOT.equals(parentTagName)) {
          final Map<String, PsiMember> properties = JavaFxPsiUtil.collectWritableProperties(myPsiClass);
          if (properties.get(name) != null) {
            return new JavaFxPropertyElementDescriptor(myPsiClass, name, false);
          }
        } else {
          final JavaFxPropertyElementDescriptor defaultPropertyDescriptor = getDefaultPropertyDescriptor();
          if (defaultPropertyDescriptor != null) {
            final String defaultPropertyName = defaultPropertyDescriptor.getName();
            if (StringUtil.equalsIgnoreCase(defaultPropertyName, name) && !StringUtil.equals(defaultPropertyName, name)) {
              final XmlElementDescriptor childDescriptor = defaultPropertyDescriptor.getElementDescriptor(childTag, contextTag);
              if (childDescriptor != null) {
                return childDescriptor;
              }
            }
          }
          final Map<String, PsiMember> properties = JavaFxPsiUtil.collectWritableProperties(myPsiClass);
          if (properties.get(name) != null) {
            return new JavaFxPropertyElementDescriptor(myPsiClass, name, false);
          }
        }
      }
    }
    if (name.length() != 0 && Character.isLowerCase(name.charAt(0))) {
      return new JavaFxPropertyElementDescriptor(myPsiClass, name, false);
    }
    return new JavaFxClassBackedElementDescriptor(name, childTag);
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(@Nullable XmlTag context) {
    if (context != null) {
      final String name = context.getName();
      if (Comparing.equal(name, getName()) && myPsiClass != null) {
        final List<XmlAttributeDescriptor> simpleAttrs = new ArrayList<XmlAttributeDescriptor>();
        collectInstanceProperties(simpleAttrs);
        collectStaticAttributesDescriptors(context, simpleAttrs);
        for (String defaultProperty : FxmlConstants.FX_DEFAULT_PROPERTIES) {
          simpleAttrs.add(new JavaFxDefaultPropertyAttributeDescriptor(defaultProperty, myPsiClass));
        }
        return simpleAttrs.isEmpty() ? XmlAttributeDescriptor.EMPTY : simpleAttrs.toArray(new XmlAttributeDescriptor[simpleAttrs.size()]);
      }
    }
    return XmlAttributeDescriptor.EMPTY;
  }

  protected void collectInstanceProperties(List<XmlAttributeDescriptor> simpleAttrs) {
    collectWritableProperties(simpleAttrs,
                              (member) -> new JavaFxPropertyAttributeDescriptor(PropertyUtil.getPropertyName(member), myPsiClass));
  }

  private <T> void collectWritableProperties(final List<T> children, final Function<PsiMember, T> factory) {
    final Map<String, PsiMember> fieldList = JavaFxPsiUtil.collectWritableProperties(myPsiClass);
    for (PsiMember field : fieldList.values()) {
      children.add(factory.fun(field));
    }
  }

  @Nullable
  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(@NonNls String attributeName, @Nullable XmlTag context) {
    if (myPsiClass == null) return null;
    if (FxmlConstants.FX_DEFAULT_PROPERTIES.contains(attributeName)) {
      return new JavaFxDefaultPropertyAttributeDescriptor(attributeName, myPsiClass);
    }
    final PsiMethod propertySetter = JavaFxPsiUtil.findStaticPropertySetter(attributeName, context);
    if (propertySetter != null) {
      return new JavaFxStaticSetterAttributeDescriptor(propertySetter, attributeName);
    }
    final PsiMember psiMember = JavaFxPsiUtil.collectWritableProperties(myPsiClass).get(attributeName);
    if (psiMember != null) {
      return new JavaFxPropertyAttributeDescriptor(attributeName, myPsiClass);
    }
    return null;
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
        host.addMessage(attribute.getNameElement(), "fx:controller can only be applied to root element", ValidationHost.ErrorType.ERROR); //todo add delete/move to upper tag fix
      }
    }
    PsiClass aClass = myPsiClass;
    final XmlAttribute constAttr = context.getAttribute(FxmlConstants.FX_CONSTANT);
    if (constAttr != null && aClass != null) {
      final PsiField constField = aClass.findFieldByName(constAttr.getValue(), true);
      if (constField != null) {
        aClass = PsiUtil.resolveClassInType(constField.getType());
      }
    } else {
      final XmlAttribute factoryAttr = context.getAttribute(FxmlConstants.FX_FACTORY);
      if (factoryAttr != null) {
        final XmlAttributeValue valueElement = factoryAttr.getValueElement();
        if (valueElement != null) {
          final PsiReference reference = valueElement.getReference();
          final PsiElement staticFactoryMethod = reference != null ? reference.resolve() : null;
          if (staticFactoryMethod instanceof PsiMethod && 
              ((PsiMethod)staticFactoryMethod).getParameterList().getParametersCount() == 0 && 
              ((PsiMethod)staticFactoryMethod).hasModifierProperty(PsiModifier.STATIC)) {
            aClass = PsiUtil.resolveClassInType(((PsiMethod)staticFactoryMethod).getReturnType());
          }
        }
      }
    }
    final String canCoerceError = JavaFxPsiUtil.isClassAcceptable(parentTag, aClass);
    if (canCoerceError != null) {
      host.addMessage(context.getNavigationElement(), canCoerceError, ValidationHost.ErrorType.ERROR);
    }
    if (aClass != null && aClass.isValid()) {
      final String message = JavaFxPsiUtil.isAbleToInstantiate(aClass);
      if (message != null) {
        host.addMessage(context, message, ValidationHost.ErrorType.ERROR);
      }
    }
  }
}
