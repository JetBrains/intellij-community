package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonClassNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFxPropertyAttributeDescriptor extends BasicXmlAttributeDescriptor {
  private final String myName;
  private final PsiClass myPsiClass;

  public JavaFxPropertyAttributeDescriptor(String name, PsiClass psiClass) {
    myName = name;
    myPsiClass = psiClass;
  }

  public PsiClass getPsiClass() {
    return myPsiClass;
  }

  @Override
  public boolean isRequired() {
    return false;
  }

  @Override
  public boolean isFixed() {
    return false;
  }

  @Override
  public boolean hasIdType() {
    return false;
  }

  @Override
  public boolean hasIdRefType() {
    return false;
  }

  @Nullable
  @Override
  public String getDefaultValue() {
    return null;
  }

  @Override
  public boolean isEnumerated() {
    return getEnumeratedValues() != null;
  }

  @Nullable
  @Override
  public String[] getEnumeratedValues() {
    final PsiClass enumClass = getEnum();
    if (enumClass != null) {
      final PsiField[] fields = enumClass.getFields();
      final List<String> enumConstants = new ArrayList<String>();
      for (PsiField enumField : fields) {
        if (isConstant(enumField)) {
          enumConstants.add(enumField.getName());
        }
      }
      return ArrayUtil.toStringArray(enumConstants);
    }

    final String propertyQName = getBoxedPropertyType(getDeclaration());
    if (CommonClassNames.JAVA_LANG_FLOAT.equals(propertyQName) || CommonClassNames.JAVA_LANG_DOUBLE.equals(propertyQName)) {
      return new String[] {"Infinity", "-Infinity", "NaN",  "-NaN"};
    } else if (CommonClassNames.JAVA_LANG_BOOLEAN.equals(propertyQName)) {
      return new String[] {"true", "false"};
    }

    return null;
  }

  protected boolean isConstant(PsiField enumField) {
    return enumField instanceof PsiEnumConstant;
  }

  protected PsiClass getEnum() {
    final PsiClass aClass = JavaFxPsiUtil.getPropertyClass(getDeclaration());
    return aClass != null && aClass.isEnum() ? aClass : null;
  }

  @Override
  public PsiElement getEnumeratedValueDeclaration(XmlElement xmlElement, String value) {
    final PsiClass aClass = getEnum();
    if (aClass != null) {
      final PsiField fieldByName = aClass.findFieldByName(value, false);
      return fieldByName != null ? fieldByName : aClass.findFieldByName(value.toUpperCase(), false);
    }
    return xmlElement;
  }

  @Nullable
  @Override
  public String validateValue(XmlElement context, String value) {
    if (context instanceof XmlAttributeValue) {
      final XmlAttributeValue xmlAttributeValue = (XmlAttributeValue)context;
      final PsiElement parent = xmlAttributeValue.getParent();
      if (parent instanceof XmlAttribute) {
        if (JavaFxPsiUtil.checkIfAttributeHandler((XmlAttribute)parent)) {
          if (value.startsWith("#")) {
            if (JavaFxPsiUtil.getControllerClass(context.getContainingFile()) == null) {
              return "No controller specified for top level element";
            }
          }
          else {
            if (JavaFxPsiUtil.parseInjectedLanguages((XmlFile)context.getContainingFile()).isEmpty()) {
              return "Page language not specified.";
            }
          }
        } else if (FxmlConstants.FX_ID.equals(((XmlAttribute)parent).getName())) {
          final PsiClass controllerClass = JavaFxPsiUtil.getControllerClass(context.getContainingFile());
          if (controllerClass != null) {
            final XmlTag xmlTag = ((XmlAttribute)parent).getParent();
            if (xmlTag != null) {
              final XmlElementDescriptor descriptor = xmlTag.getDescriptor();
              if (descriptor instanceof JavaFxClassBackedElementDescriptor) {
                final PsiElement declaration = descriptor.getDeclaration();
                if (declaration instanceof PsiClass) {
                  final PsiField fieldByName = controllerClass.findFieldByName(xmlAttributeValue.getValue(), false);
                  if (fieldByName != null && !InheritanceUtil.isInheritorOrSelf((PsiClass)declaration, PsiUtil.resolveClassInType(fieldByName.getType()), true)) {
                    return "Cannot set " + ((PsiClass)declaration).getQualifiedName() + " to field \'" + fieldByName.getName() + "\'";
                  }
                }
              }
            }
          }
        }
        else {
          final XmlAttributeDescriptor attributeDescriptor = ((XmlAttribute)parent).getDescriptor();
          if (attributeDescriptor != null) {
            final PsiElement declaration = attributeDescriptor.getDeclaration();
            final String boxedQName;
            if (declaration != null) {
              boxedQName = getBoxedPropertyType(declaration);
            }
            else {
              final PsiClass tagClass = JavaFxPsiUtil.getTagClass((XmlAttributeValue)context);
              if (tagClass != null && !InheritanceUtil.isInheritor(tagClass, false, JavaFxCommonClassNames.JAVAFX_SCENE_NODE)) {
                boxedQName = tagClass.getQualifiedName();
              }
              else {
                boxedQName = null;
              }
            }
            if (boxedQName != null) {
              try {
                final Class<?> aClass = Class.forName(boxedQName);
                final Method method = aClass.getMethod(JavaFxCommonClassNames.VALUE_OF, String.class);
                method.invoke(aClass, ((XmlAttributeValue)context).getValue());
              }
              catch (InvocationTargetException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof NumberFormatException) {
                  final PsiReference reference = context.getReference();
                  if (reference != null) {
                    final PsiElement resolve = reference.resolve();
                    if (resolve instanceof XmlAttributeValue) {
                      final PsiClass tagClass = JavaFxPsiUtil.getTagClass((XmlAttributeValue)resolve);
                      if (tagClass != null && boxedQName.equals(tagClass.getQualifiedName())) {
                        return null;
                      }
                    }
                  }
                  return "Invalid value: unable to coerce to " + boxedQName;
                }
              }
              catch (Throwable ignore) {
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static String getBoxedPropertyType(PsiElement declaration) {
    PsiType attrType = null;
    if (declaration instanceof PsiField) {
      attrType = JavaFxPsiUtil.getWrappedPropertyType((PsiField)declaration, declaration.getProject(), JavaFxCommonClassNames.ourWritableMap);
    } else if (declaration instanceof PsiMethod) {
      final PsiParameter[] parameters = ((PsiMethod)declaration).getParameterList().getParameters();
      final boolean isStatic = ((PsiMethod)declaration).hasModifierProperty(PsiModifier.STATIC);
      if (isStatic && parameters.length == 2 || !isStatic && parameters.length == 1) {
        attrType = parameters[parameters.length - 1].getType();
      }
    }

    String boxedQName = null;
    if (attrType instanceof PsiPrimitiveType) {
      boxedQName = ((PsiPrimitiveType)attrType).getBoxedTypeName();
    } else if (PsiPrimitiveType.getUnboxedType(attrType) != null) {
      final PsiClass attrClass = PsiUtil.resolveClassInType(attrType);
      boxedQName = attrClass != null ? attrClass.getQualifiedName() : null;
    }
    return boxedQName;
  }

  @Override
  public PsiElement getDeclaration() {
    if (myPsiClass != null) {
      final PsiField field = myPsiClass.findFieldByName(myName, true);
      if (field != null) {
        return field;
      }
      return JavaFxPsiUtil.findPropertySetter(myName, myPsiClass);
    }
    return null;
  }

  @Override
  public PsiReference[] getValueReferences(XmlElement element, @NotNull String text) {
    return !text.startsWith("${") ? super.getValueReferences(element, text) : PsiReference.EMPTY_ARRAY;
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
}
