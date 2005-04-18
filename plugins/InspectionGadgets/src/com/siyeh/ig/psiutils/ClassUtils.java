package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.openapi.project.Project;

import java.util.HashSet;
import java.util.Set;

public class ClassUtils {


    private static final int NUM_IMMUTABLE_TYPES = 17;
    /** @noinspection StaticCollection*/
    private static final Set immutableTypes = new HashSet(NUM_IMMUTABLE_TYPES);
    /** @noinspection StaticCollection*/
    private static final Set primitiveNumericTypes = new HashSet(6);
    /** @noinspection StaticCollection*/
    private static final Set numericTypes = new HashSet(6);
    /** @noinspection StaticCollection*/
    private static final Set integralTypes = new HashSet(10);

    static
    {
        integralTypes.add(PsiType.LONG);
        integralTypes.add(PsiType.INT);
        integralTypes.add(PsiType.SHORT);
        integralTypes.add(PsiType.CHAR);
        integralTypes.add(PsiType.BYTE);

        primitiveNumericTypes.add(PsiType.BYTE);
        primitiveNumericTypes.add(PsiType.CHAR);
        primitiveNumericTypes.add(PsiType.SHORT);
        primitiveNumericTypes.add(PsiType.INT);
        primitiveNumericTypes.add(PsiType.LONG);
        primitiveNumericTypes.add(PsiType.FLOAT);
        primitiveNumericTypes.add(PsiType.DOUBLE);

        immutableTypes.add("java.lang.Boolean");
        immutableTypes.add("java.lang.Char");
        immutableTypes.add("java.lang.Short");
        immutableTypes.add("java.lang.Integer");
        immutableTypes.add("java.lang.Long");
        immutableTypes.add("java.lang.Float");
        immutableTypes.add("java.lang.Double");
        immutableTypes.add("java.lang.Byte");
        immutableTypes.add("java.lang.String");
        immutableTypes.add("java.awt.Font");
        immutableTypes.add("java.awt.Color");

        numericTypes.add("java.lang.Byte");
        numericTypes.add("java.lang.Short");
        numericTypes.add("java.lang.Integer");
        numericTypes.add("java.lang.Long");
        numericTypes.add("java.lang.Float");
        numericTypes.add("java.lang.Double");
    }

    private ClassUtils(){
        super();
    }
    
    public static boolean isSubclass(PsiClass aClass, String ancestorName) {
        final PsiManager psiManager = aClass.getManager();
        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiClass ancestorClass = psiManager.findClass(ancestorName, scope);
        return InheritanceUtil.isInheritorOrSelf(aClass, ancestorClass, true);
    }

    public static boolean isPrimitive(PsiType type) {
        return TypeConversionUtil.isPrimitiveAndNotNull(type);
    }

    public static boolean isIntegral(PsiType type) {
        return integralTypes.contains(type);
    }

    public static boolean isImmutable(PsiType type) {
        if(TypeConversionUtil.isPrimitiveAndNotNull(type))
        {
            return true;
        }
        if(!(type instanceof PsiClassType)){
            return false;
        }
        final PsiClassType classType = (PsiClassType) type;
        final String className = classType.getClassName();
        return immutableTypes.contains(className);
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

    public static boolean isWrappedNumericType(PsiType type) {
        if(!(type instanceof PsiClassType)){
            return false;
        }
        final PsiClassType classType = (PsiClassType) type;
        final String className = classType.getClassName();
        return numericTypes.contains(className);
    }

    public static boolean isPrimitiveNumericType(PsiType type) {
        return primitiveNumericTypes.contains(type);
    }

    public static boolean isInnerClass(PsiClass aClass) {
        final PsiClass parentClass = getContainingClass(aClass);
        return parentClass != null;
    }

    public static PsiClass getContainingClass(PsiElement aClass) {
        return (PsiClass) PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
    }

    public static PsiClass getOutermostContainingClass(PsiClass aClass) {
        PsiClass outerClass = aClass;
        while (true) {
            final PsiClass containingClass =
                    ClassUtils.getContainingClass(outerClass);
            if (containingClass != null) {
                outerClass = containingClass;
            } else {
                return outerClass;
            }
        }
    }

    public static PsiMethod getContainingMethod(PsiElement element){
        return (PsiMethod) PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    }

    public static boolean isClassVisibleFromClass(PsiClass baseClass,
                                               PsiClass referencedClass){
        if(referencedClass.hasModifierProperty(PsiModifier.PUBLIC))
        {
            return true;
        }
        else if(referencedClass.hasModifierProperty(PsiModifier.PROTECTED))
        {
            return inSamePackage(baseClass, referencedClass);
        }
        else if(referencedClass.hasModifierProperty(PsiModifier.PRIVATE))
        {
            return PsiTreeUtil.findCommonParent(baseClass, referencedClass)!=null;
        }
        else
        {
            return inSamePackage(baseClass, referencedClass);
        }
    }
}
