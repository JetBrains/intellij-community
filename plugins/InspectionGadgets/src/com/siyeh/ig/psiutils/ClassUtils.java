package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.HashSet;
import java.util.Set;

public class ClassUtils {
    private ClassUtils() {
        super();
    }

    private static final int NUM_IMMUTABLE_TYPES = 17;
    private static final Set s_immutableTypes = new HashSet(NUM_IMMUTABLE_TYPES);
    private static final Set s_primitiveNumericTypes = new HashSet(6);
    private static final Set s_numericTypes = new HashSet(6);

    static {
        s_primitiveNumericTypes.add("byte");
        s_primitiveNumericTypes.add("char");
        s_primitiveNumericTypes.add("short");
        s_primitiveNumericTypes.add("int");
        s_primitiveNumericTypes.add("long");
        s_primitiveNumericTypes.add("float");
        s_primitiveNumericTypes.add("double");

        s_immutableTypes.add("boolean");
        s_immutableTypes.add("char");
        s_immutableTypes.add("short");
        s_immutableTypes.add("int");
        s_immutableTypes.add("long");
        s_immutableTypes.add("float");
        s_immutableTypes.add("double");
        s_immutableTypes.add("byte");
        s_immutableTypes.add("java.lang.Boolean");
        s_immutableTypes.add("java.lang.Char");
        s_immutableTypes.add("java.lang.Short");
        s_immutableTypes.add("java.lang.Integer");
        s_immutableTypes.add("java.lang.Long");
        s_immutableTypes.add("java.lang.Float");
        s_immutableTypes.add("java.lang.Double");
        s_immutableTypes.add("java.lang.Byte");
        s_immutableTypes.add("java.lang.String");
        s_immutableTypes.add("java.awt.Font");
        s_immutableTypes.add("java.awt.Color");

        s_numericTypes.add("java.lang.Byte");
        s_numericTypes.add("java.lang.Short");
        s_numericTypes.add("java.lang.Integer");
        s_numericTypes.add("java.lang.Long");
        s_numericTypes.add("java.lang.Float");
        s_numericTypes.add("java.lang.Double");
    }

    public static boolean isSubclass(PsiClass aClass, String ancestorName) {
        return isSubclass(aClass, ancestorName, new HashSet());
    }

    private static boolean isSubclass(PsiClass aClass, String ancestorName, Set alreadyChecked) {
        PsiClass currentClass = aClass;
        while (currentClass != null) {
            final String className = currentClass.getQualifiedName();
            if (className != null) {
                if (alreadyChecked.contains(className)) {
                    return false;
                } else if (className.equals(ancestorName)) {
                    return true;
                }
            }
            alreadyChecked.add(className);
            currentClass = currentClass.getSuperClass();
        }
        return false;
    }

    public static boolean isPrimitive(PsiType type) {
        return
                type.equals(PsiType.BOOLEAN) ||
                type.equals(PsiType.LONG) ||
                type.equals(PsiType.INT) ||
                type.equals(PsiType.SHORT) ||
                type.equals(PsiType.CHAR) ||
                type.equals(PsiType.BYTE) ||
                type.equals(PsiType.FLOAT) ||
                type.equals(PsiType.DOUBLE);
    }

    public static boolean isIntegral(PsiType type) {
        return
                type.equals(PsiType.LONG) ||
                type.equals(PsiType.INT) ||
                type.equals(PsiType.SHORT) ||
                type.equals(PsiType.CHAR) ||
                type.equals(PsiType.BYTE);
    }

    public static boolean isImmutable(PsiType type) {
        final String typeName = type.getCanonicalText();
        return s_immutableTypes.contains(typeName);
    }

    private static boolean inSamePackage(PsiClass class1, PsiClass class2) {
        final String className1 = class1.getQualifiedName();
        if (className1 == null) {
            return false;
        }
        final int packageLength1 = className1.lastIndexOf((int) '.');
        final String classPackageName1;
        if (packageLength1 == -1) {
            classPackageName1 = "";
        } else {
            classPackageName1 = className1.substring(0, packageLength1);
        }
        final String className2 = class2.getQualifiedName();
        if (className2 == null) {
            return false;
        }
        final int packageLength2 = className2.lastIndexOf((int) '.');
        final String classPackageName2;
        if (packageLength2 == -1) {
            classPackageName2 = "";
        } else {
            classPackageName2 = className2.substring(0, packageLength2);
        }
        return classPackageName1.equals(classPackageName2);
    }

    public static boolean isFieldVisible(PsiField field, PsiClass fromClass) {
        final PsiClass fieldClass = field.getContainingClass();

        if (fieldClass.equals(fromClass)) {
            return true;
        }
        if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
            return false;
        }
        if (field.hasModifierProperty(PsiModifier.PUBLIC) ||
                field.hasModifierProperty(PsiModifier.PROTECTED)) {
            return true;
        }

        return inSamePackage(fieldClass, fromClass);
    }

    public static boolean isBuiltInNumericType(PsiType type) {
        final String typeName = type.getCanonicalText();
        return s_numericTypes.contains(typeName);
    }

    public static boolean isPrimitiveNumericType(PsiType type) {
        final String typeName = type.getCanonicalText();
        return s_primitiveNumericTypes.contains(typeName);
    }

    public static boolean isInnerClass(PsiClass aClass) {
        final PsiClass parentClass = getContainingClass(aClass);
        return parentClass != null;
    }

    public static PsiClass getContainingClass(PsiElement aClass) {
        final PsiClass parentClass =
                (PsiClass) PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
        return parentClass;
    }

    public static PsiClass getOutermostContainingClass(PsiClass aClass) {
        PsiClass outerClass = aClass;
        while (true) {
            final PsiClass containingClass = (PsiClass) PsiTreeUtil.getParentOfType(outerClass, PsiClass.class);
            if (containingClass != null) {
                outerClass = containingClass;
            } else {
                break;
            }
        }
        return outerClass;
    }
}
