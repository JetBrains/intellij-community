/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.generate.tostring.psi;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.generate.tostring.util.StringUtil;

/**
 * Basic PSI Adapter with common function that works in all supported versions of IDEA.
 */
public abstract class PsiAdapter {

    /**
     * Constructor - use {@link PsiAdapterFactory}.
     */
    protected PsiAdapter() {
    }

  /**
     * Finds the class for the given element.
     * <p/>
     * Will look in the element's parent hieracy.
     *
     * @param element element to find it's class
     * @return the class, null if not found.
     */
    @Nullable
    public PsiClass findClass(PsiElement element) {
        if (element instanceof PsiClass) {
            return (PsiClass) element;
        }

        if (element.getParent() != null) {
            return findClass(element.getParent());
        }

        return null;
    }

    /**
     * Returns true if a field is constant.
     * <p/>
     * This is identifed as the name of the field is only in uppercase and it has
     * a <code>static</code> modifier.
     *
     * @param field field to check if it's a constant
     * @return true if constant.
     */
    public boolean isConstantField(PsiField field) {
        PsiModifierList list = field.getModifierList();
        if (list == null) {
            return false;
        }

        // modifier must be static
        if (!list.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }

        // name must NOT have any lowercase character
        return !StringUtil.hasLowerCaseChar(field.getName());
    }

    /**
     * Find's an existing method with the given name.
     * If there isn't a method with the name, null is returned.
     *
     * @param clazz the class
     * @param name  name of method to find
     * @return the found method, null if none exist
     */
    @Nullable
    public PsiMethod findMethodByName(PsiClass clazz, String name) {
        PsiMethod[] methods = clazz.getMethods();

        // use reverse to find from botton as the duplicate conflict resolution policy requires this
        for (int i = methods.length - 1; i >= 0; i--) {
            PsiMethod method = methods[i];
            if (name.equals(method.getName()))
                return method;
        }
        return null;
    }

    /**
     * Returns true if the given field a primtive array type (e.g., int[], long[], float[]).
     *
     * @param type type.
     * @return true if field is a primitve array type.
     */
    public boolean isPrimitiveArrayType(PsiType type) {
        return type instanceof PsiArrayType && isPrimitiveType(((PsiArrayType) type).getComponentType());
    }

    /**
     * Is the type an Object array type (etc. String[], Object[])?
     *
     * @param type type.
     * @return true if it's an Object array type.
     */
    public boolean isObjectArrayType(PsiType type) {
        return type instanceof PsiArrayType && !isPrimitiveType(((PsiArrayType) type).getComponentType());
    }

    /**
     * Is the type a String array type (etc. String[])?
     *
     * @param type type.
     * @return true if it's a String array type.
     */
    public boolean isStringArrayType(PsiType type) {
        if (isPrimitiveType(type))
            return false;

        return type.getCanonicalText().indexOf("String[]") > 0;
    }

    /**
     * Is the given field a {@link java.util.Collection} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Collection type.
     */
    public boolean isCollectionType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, "java.util.Collection");
    }

    /**
     * Is the given field a {@link java.util.Map} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Map type.
     */
    public boolean isMapType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, "java.util.Map");
    }

    /**
     * Is the given field a {@link java.util.Set} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Map type.
     */
    public boolean isSetType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, "java.util.Set");
    }

    /**
     * Is the given field a {@link java.util.List} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Map type.
     */
    public boolean isListType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, "java.util.List");
    }

    /**
     * Is the given field a {@link java.lang.String} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a String type.
     */
    public boolean isStringType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, "java.lang.String");
    }

    /**
     * Is the given field assignable from {@link java.lang.Object}?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's an Object type.
     */
    public boolean isObjectType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, "java.lang.Object");
    }

    /**
     * Is the given field a {@link java.util.Date} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Date type.
     */
    public boolean isDateType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, "java.util.Date");
    }

    /**
     * Is the given field a {@link java.util.Calendar} type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Calendar type.
     */
    public boolean isCalendarType(PsiElementFactory factory, PsiType type) {
        return isTypeOf(factory, type, "java.util.Calendar");
    }

    /**
     * Is the given field a {@link java.lang.Boolean} type or a primitive boolean type?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a Boolean or boolean type.
     */
    public boolean isBooleanType(PsiElementFactory factory, PsiType type) {
        if (isPrimitiveType(type)) {
            // test for simple type of boolean
            String s = type.getCanonicalText();
            return "boolean".equals(s);
        } else {
            // test for Object type of Boolean
            return isTypeOf(factory, type, "java.lang.Boolean");
        }
    }

    /**
     * Is the given field a numeric type (assignable from java.lang.Numeric or a primitive type of byte, short, int, long, float, double type)?
     *
     * @param factory element factory.
     * @param type    type.
     * @return true if it's a numeric type.
     */
    public boolean isNumericType(PsiElementFactory factory, PsiType type) {
        if (isPrimitiveType(type)) {
            // test for simple type of numeric
            String s = type.getCanonicalText();
            return "byte".equals(s) || "double".equals(s) || "float".equals(s) || "int".equals(s) || "long".equals(s) || "short".equals(s);
        } else {
            // test for Object type of numeric
            return isTypeOf(factory, type, "java.lang.Number");
        }
    }

  /**
     * Does the javafile have the import statement?
     *
     * @param javaFile        javafile.
     * @param importStatement import statement to test existing for.
     * @return true if the javafile has the import statement.
     */
    public boolean hasImportStatement(PsiJavaFile javaFile, String importStatement) {
        PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return false;
        }

        if (importStatement.endsWith(".*")) {
            return (importList.findOnDemandImportStatement(fixImportStatement(importStatement)) != null);
        } else {
            return (importList.findSingleClassImportStatement(importStatement) != null);
        }
    }

    /**
     * Add's an importstatement to the javafile and optimizes the imports afterwards.
     *
     * @param javaFile                javafile.
     * @param importStatementOnDemand name of importstatement, must be with a wildcard (etc. java.util.*).
     * @param factory                 PSI element factory.
     * @throws com.intellij.util.IncorrectOperationException
     *          is thrown if there is an error creating the importstatement.
     */
    public void addImportStatement(PsiJavaFile javaFile, String importStatementOnDemand, PsiElementFactory factory) throws IncorrectOperationException {
        PsiImportStatement is = factory.createImportStatementOnDemand(fixImportStatement(importStatementOnDemand));

        // add the import to the file, and optimize the imports
        PsiImportList importList = javaFile.getImportList();
        if (importList != null) {
            importList.add(is);
        }

        JavaCodeStyleManager.getInstance(javaFile.getProject()).optimizeImports(javaFile);
    }

    /**
     * Fixes the import statement to be returned as packagename only (without .* or any Classname).
     * <p/>
     * <br/>Example: java.util will be returned as java.util
     * <br/>Example: java.util.* will be returned as java.util
     * <br/>Example: java.text.SimpleDateFormat will be returned as java.text
     *
     * @param importStatementOnDemand import statement
     * @return import statement only with packagename
     */
    private String fixImportStatement(String importStatementOnDemand) {
        if (importStatementOnDemand.endsWith(".*")) {
            return importStatementOnDemand.substring(0, importStatementOnDemand.length() - 2);
        } else {
            boolean hasClassname = StringUtil.hasUpperCaseChar(importStatementOnDemand);

            if (hasClassname) {
                // extract packagename part
                int pos = importStatementOnDemand.lastIndexOf(".");
                return importStatementOnDemand.substring(0, pos);
            } else {
                // it is a pure packagename
                return importStatementOnDemand;
            }
        }
    }

    /**
     * Does this class have a super class?
     * <p/>
     * If the class just extends java.lang.Object then false is returned.
     * Extending java.lang.Object is <b>not</b> concidered the class to have a super class.
     *
     * @param project the IDEA project
     * @param clazz   the class to test
     * @return true if this class extends another class.
     */
    public boolean hasSuperClass(Project project, PsiClass clazz) {
        PsiClass superClass = getSuperClass(project, clazz);
        if (superClass == null) {
            return false;
        }

        return (!"Object".equals(superClass.getName()));
    }

  /**
     * Get's the fields fully qualified classname (etc java.lang.String, java.util.ArrayList)
     *
     * @param type the type.
     * @return the fully qualified classname, null if the field is a primitive.
     * @see #getTypeClassName(com.intellij.psi.PsiType) for the non qualified version.
     */
    @Nullable
    public String getTypeQualifiedClassName(PsiType type) {
        if (isPrimitiveType(type)) {
            return null;
        }

        // avoid [] if the type is an array
        String name = type.getCanonicalText();
        if (name.endsWith("[]")) {
            return name.substring(0, name.length() - 2);
        }

        return name;
    }

    /**
     * Get's the fields classname (etc. String, ArrayList)
     *
     * @param type the type.
     * @return the classname, null if the field is a primitive.
     * @see #getTypeQualifiedClassName(com.intellij.psi.PsiType) for the qualified version.
     */
    @Nullable
    public String getTypeClassName(PsiType type) {
        String name = getTypeQualifiedClassName(type);

        // return null if it was a primitive type
        if (name == null) {
            return null;
        }

        int i = name.lastIndexOf('.');
        return name.substring(i + 1, name.length());
    }

  /**
     * Finds the public static void main(String[] args) method.
     *
     * @param clazz the class.
     * @return the method if it exists, null if not.
     */
    @Nullable
    public PsiMethod findPublicStaticVoidMainMethod(PsiClass clazz) {
        PsiMethod[] methods = clazz.findMethodsByName("main", false);

        // is it public static void main(String[] args)
        for (PsiMethod method : methods) {
            // must be public
            if (!method.hasModifierProperty("public")) {
                continue;
            }

            // must be static
            if (!method.hasModifierProperty("static")) {
                continue;
            }

            // must have void as return type
            PsiType returnType = method.getReturnType();
            if (returnType == null || returnType.equalsToText("void")) {
                continue;
            }

            // must have one parameter
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length != 1) {
                continue;
            }

            // parameter must be string array
            if (!isStringArrayType(parameters[0].getType())) {
                continue;
            }

            // public static void main(String[] args) method found
            return method;
        }

        // main not found
        return null;
    }

    /**
     * Add or replaces the javadoc comment to the given method.
     *
     * @param factory          element factory.
     * @param codeStyleManager CodeStyleManager.
     * @param method           the method the javadoc should be added/set to.
     * @param javadoc          the javadoc comment.
     * @param replace          true if any existing javadoc should be replaced. false will not replace any existing javadoc and thus leave the javadoc untouched.
     * @return the added/replace javadoc comment, null if the was an existing javadoc and it should <b>not</b> be replaced.
     * @throws IncorrectOperationException is thrown if error adding/replacing the javadoc comment.
     */
    @Nullable
    public PsiComment addOrReplaceJavadoc(PsiElementFactory factory, CodeStyleManager codeStyleManager, PsiMethod method, String javadoc, boolean replace) throws IncorrectOperationException {
        PsiComment comment = factory.createCommentFromText(javadoc, null);

        // does a method already exists?
        PsiDocComment doc = method.getDocComment();
        if (doc != null) {
            if (replace) {
                // javadoc already exists, so replace
                doc.replace(comment);
                codeStyleManager.reformat(method); // to reformat javadoc
                return comment;
            } else {
                // do not replace existing javadoc
                return null;
            }
        } else {
            // add new javadoc
            method.addBefore(comment, method.getFirstChild());
            codeStyleManager.reformat(method); // to reformat javadoc
            return comment;
        }
    }

  /**
     * Find's an existing field with the given name.
     * If there isn't a field with the name, null is returned.
     *
     * @param clazz the class
     * @param name  name of field to find
     * @return the found field, null if none exist
     */
    @Nullable
    public PsiField findFieldByName(PsiClass clazz, String name) {
        PsiField[] fields = clazz.getFields();

        // use reverse to find from botton as the duplicate conflict resolution policy requires this
        for (int i = fields.length - 1; i >= 0; i--) {
            PsiField field = fields[i];
            if (name.equals(field.getName()))
                return field;
        }

        return null;
    }

    /**
     * Is the given type a "void" type.
     *
     * @param type the type.
     * @return true if a void type, false if not.
     */
    public boolean isTypeOfVoid(PsiType type) {
        return (type != null && type.equalsToText("void"));
    }

    /**
     * Is the method a getter method?
     * <p/>
     * The name of the method must start with <code>get</code> or <code>is</code>.
     * And if the method is a <code>isXXX</code> then the method must return a java.lang.Boolean or boolean.
     *
     * @param factory element factory.
     * @param method  the method
     * @return true if a getter method, false if not.
     */
    public boolean isGetterMethod(PsiElementFactory factory, PsiMethod method) {
        // must not be a void method
        if (isTypeOfVoid(method.getReturnType())) {
            return false;
        }

        if (method.getName().matches("^(is|has)\\p{Upper}.*")) {
            return isBooleanType(factory, method.getReturnType());
        } else if (method.getName().matches("^(get)\\p{Upper}.*")) {
            return true;
        }

        return false;
    }

    /**
     * Get's the field name of the getter method.
     * <p/>
     * The method must be a getter method for a field.
     * Returns null if this method is not a getter.
     * <p/>
     * The fieldname is the part of the name that is after the <code>get</code> or <code>is</code> part
     * of the name.
     * <p/>
     * Example: methodName=getName will return fieldname=name
     *
     * @param factory element factory.
     * @param method  the method
     * @return the fieldname if this is a getter method.
     * @see #isGetterMethod(com.intellij.psi.PsiElementFactory,com.intellij.psi.PsiMethod) for the getter check
     */
    @Nullable
    public String getGetterFieldName(PsiElementFactory factory, PsiMethod method) {
        // must be a getter
        if (!isGetterMethod(factory, method)) {
            return null;
        }

        // return part after get
        String getName = StringUtil.after(method.getName(), "get");
        if (getName != null) {
            getName = StringUtil.firstLetterToLowerCase(getName);
            return getName;
        }

        // return part after is
        String isName = StringUtil.after(method.getName(), "is");
        if (isName != null) {
            isName = StringUtil.firstLetterToLowerCase(isName);
            return isName;
        }

        return null;
    }

  /**
     * Returns true if the field is enum (JDK1.5).
     *
     * @param field   field to check if it's a enum
     * @return true if enum.
     */
    public boolean isEnumField(PsiField field) {
        PsiType type = field.getType();

        // must not be an primitive type
        if (isPrimitiveType(type)) {
            return false;
        }

        GlobalSearchScope scope = type.getResolveScope();
        if (scope == null) {
            return false;
        }

        // find the class
        String name = type.getCanonicalText();
        PsiClass clazz = JavaPsiFacade.getInstance(field.getProject()).findClass(name, scope);
        if (clazz == null) {
            return false;
        }

        return clazz.isEnum();
  }

    /**
     * Is the class an exception - extends Throwable (will check super).
     *
     * @param clazz class to check.
     * @return true if class is an exception.
     */
    public static boolean isExceptionClass(PsiClass clazz) {
      return InheritanceUtil.isInheritor(clazz, CommonClassNames.JAVA_LANG_THROWABLE);
    }

    /**
     * Is the class an abstract class
     *
     * @param clazz class to check.
     * @return true if class is abstract.
     */
    public boolean isAbstractClass(PsiClass clazz) {
        PsiModifierList list = clazz.getModifierList();
        if (list == null) {
            return false;
        }
        return clazz.getModifierList().hasModifierProperty(PsiModifier.ABSTRACT);
    }

  /**
     * Finds the public boolean equals(Object o) method.
     *
     * @param clazz the class.
     * @return the method if it exists, null if not.
     */
    @Nullable
    public PsiMethod findEqualsMethod(PsiClass clazz) {
        PsiMethod[] methods = clazz.findMethodsByName("equals", false);

        // is it public boolean equals(Object o)
        for (PsiMethod method : methods) {
            // must be public
            if (!method.hasModifierProperty("public")) {
                continue;
            }

            // must not be static
          if (method.hasModifierProperty("static")) {
            continue;
          }

            // must have boolean as return type
            PsiType returnType = method.getReturnType();
            if (returnType == null || !returnType.equalsToText("boolean")) {
                continue;
            }

            // must have one parameter
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length != 1) {
                continue;
            }

            // parameter must be Object
            if (!(parameters[0].getType().getCanonicalText().equals("java.lang.Object"))) {
                continue;
            }

            // equals method found
            return method;
        }

        // equals not found
        return null;
    }

    /**
     * Finds the public int hashCode() method.
     *
     * @param clazz the class.
     * @return the method if it exists, null if not.
     */
    @Nullable
    public PsiMethod findHashCodeMethod(PsiClass clazz) {
        PsiMethod[] methods = clazz.findMethodsByName("hashCode", false);

        // is it public int hashCode()
        for (PsiMethod method : methods) {
            // must be public
            if (!method.hasModifierProperty("public")) {
                continue;
            }

            // must not be static
          if (method.hasModifierProperty("static")) {
            continue;
          }

            // must have int as return type
            PsiType returnType = method.getReturnType();
            if (returnType == null || !returnType.equalsToText("int")) {
                continue;
            }

            // must not have a parameter
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length != 0) {
                continue;
            }

            // hashCode method found
            return method;
        }

        // hashCode not found
        return null;
    }

    /**
     * Adds/replaces the given annotation text to the method.
     *
     * @param factory    element factory.
     * @param method     the method the javadoc should be added/set to.
     * @param annotation the annotation as text.
     * @return the added annotation object
     * @throws IncorrectOperationException is thrown if error adding/replacing the javadoc comment.
     */
    public PsiAnnotation addAnnotationToMethod(PsiElementFactory factory, PsiMethod method, String annotation) throws IncorrectOperationException {
        PsiAnnotation ann = method.getModifierList().findAnnotation(annotation);
        if (ann == null) {
            // add new annotation
            ann = factory.createAnnotationFromText(annotation, method.getModifierList());
            PsiModifierList modifierList = method.getModifierList();
            modifierList.addBefore(ann, modifierList.getFirstChild());
        } else {
            PsiModifierList modifierList = method.getModifierList();
            modifierList.replace(ann); // already exist so replace
        }

        return ann;
    }

    /**
     * Check if the given type against a FQ classname (assignable).
     *
     * @param factory         IDEA factory
     * @param type            the type
     * @param typeFQClassName the FQ classname to test against.
     * @return true if the given type is assigneable of FQ classname.
     */
    protected boolean isTypeOf(PsiElementFactory factory, PsiType type, String typeFQClassName) {
        // fix for IDEA where fields can have 'void' type and generate NPE.
        if (isTypeOfVoid(type)) {
            return false;
        }

        if (isPrimitiveType(type)) {
            return false;
        }

        GlobalSearchScope scope = type.getResolveScope();
        if (scope == null) {
            return false;
        }
        PsiType typeTarget = factory.createTypeByFQClassName(typeFQClassName, scope);
        return typeTarget.isAssignableFrom(type);
    }

    /**
     * Get's the superclass.
     *
     * @param project IDEA project
     * @param clazz   the class
     * @return the super, null if not found.
     */
    @Nullable
    public PsiClass getSuperClass(Project project, PsiClass clazz) {
        PsiReferenceList list = clazz.getExtendsList();

        // check if no superclass at all
        if (list == null || list.getReferencedTypes().length != 1) {
            return null;
        }

        // have superclass get it [0] is the index of the superclass (a class can not extend more than one class)
        GlobalSearchScope scope = list.getReferencedTypes()[0].getResolveScope();
        String classname = list.getReferencedTypes()[0].getCanonicalText();

        return JavaPsiFacade.getInstance(project).findClass(classname, scope);
    }

    /**
     * Get's the names the given class implements (not FQ names).
     *
     * @param clazz the class
     * @return the names.
     */
    public String[] getImplementsClassnames(PsiClass clazz) {
        PsiClass[] interfaces = clazz.getInterfaces();

        if (interfaces == null || interfaces.length == 0) {
          return ArrayUtil.EMPTY_STRING_ARRAY;
        }

        String[] names = new String[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            PsiClass anInterface = interfaces[i];
            names[i] = anInterface.getName();
        }

        return names;
    }

    /**
     * Is the given type a primitive?
     *
     * @param type the type.
     * @return true if primitive, false if not.
     */
    public boolean isPrimitiveType(PsiType type) {
        return type instanceof PsiPrimitiveType;
    }

    /**
     * Executes the given runable in IDEA command.
     *
     * @param project IDEA project
     * @param runable the runable task to exexute.
     */
    public void executeCommand(Project project, Runnable runable) {
        CommandProcessor.getInstance().executeCommand(project, runable, "GenerateToString", null);
    }

    /**
     * Add's the interface name to the class implementation list.
     *
     * @param project       IDEA project
     * @param clazz         the class
     * @param interfaceName the interface name the class should implement
     * @throws IncorrectOperationException is thrown by IDEA.
     */
    public void addImplements(Project project, PsiClass clazz, String interfaceName) throws IncorrectOperationException {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

        // get the interface class
        PsiClass interfaceClass = facade.findClass(interfaceName, GlobalSearchScope.allScope(project));

        // if the interface exists add it as a reference in the implements list
        if (interfaceClass != null) {
            PsiJavaCodeReferenceElement ref = facade.getElementFactory().createClassReferenceElement(interfaceClass);
            PsiReferenceList list = clazz.getImplementsList();
            if (list != null) {
                list.add(ref);
            }
        }
    }

  /**
     * Get's the full filename to this plugin .jar file
     *
     * @return the full filename to this plugin .jar file
     */
    public abstract String getPluginFilename();

}
