package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFxPropertyAttributeDescriptor implements XmlAttributeDescriptor {
  private final String myName;
  private final PsiClass myPsiClass;

  public JavaFxPropertyAttributeDescriptor(String name, PsiClass psiClass) {
    myName = name;
    myPsiClass = psiClass;
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
    return getEnum() != null;
  }

  @Nullable
  @Override
  public String[] getEnumeratedValues() {
    final PsiClass enumClass = getEnum();
    if (enumClass != null) {
      final PsiField[] fields = enumClass.getFields();
      final List<String> enumConstants = new ArrayList<String>();
      for (PsiField enumField : fields) {
        if (enumField instanceof PsiEnumConstant) {
          enumConstants.add(enumField.getName());
        }
      }
      return ArrayUtil.toStringArray(enumConstants);
    }
    return null;
  }

  private PsiClass getEnum() {
    final PsiElement field = getDeclaration();
    if (field instanceof PsiField) {
      final PsiType type = ((PsiField)field).getType();
      if (type instanceof PsiClassType) {
        final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
        final PsiClass attributeClass = resolveResult.getElement();
        if (attributeClass != null) {
          final PsiClass objectProperty = JavaPsiFacade.getInstance(attributeClass.getProject())
            .findClass(JavaFxCommonClassNames.JAVAFX_BEANS_PROPERTY_OBJECT_PROPERTY, attributeClass.getResolveScope());
          if (objectProperty != null) {
            final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getClassSubstitutor(objectProperty, attributeClass, resolveResult.getSubstitutor());
            if (superClassSubstitutor != null) {
              final PsiType propertyType = superClassSubstitutor.substitute(objectProperty.getTypeParameters()[0]);
              if (propertyType instanceof PsiClassType) {
                final PsiClass psiClass = ((PsiClassType)propertyType).resolve();
                if (psiClass != null && psiClass.isEnum()) {
                  return psiClass;
                }
              }
            }
          }
        }
      }
    }
    return null;
  }
  
  @Nullable
  @Override
  public String validateValue(XmlElement context, String value) {
    if (isEnumerated()) {
      final String[] values = getEnumeratedValues();
      if (values != null && !isWithingBounds(value, values)) {
        return value + " is not withing its bounds";
      }
    }
    return null;
  }

  private static boolean isWithingBounds(String value, String[] values) {
    for (String enumConstant : values) {
      if (StringUtil.endsWithIgnoreCase(enumConstant, value)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public PsiElement getDeclaration() {
    return myPsiClass != null ? myPsiClass.findFieldByName(myName, true) : null;
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
  public void init(PsiElement element) {}

  @Override
  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
