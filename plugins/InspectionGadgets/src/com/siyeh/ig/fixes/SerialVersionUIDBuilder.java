/*
 * Copyright 2003-2006 Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class SerialVersionUIDBuilder extends PsiRecursiveElementVisitor{

    @NonNls private static final String ACCESS_METHOD_NAME_PREFIX = "access$";

    private static final String SERIALIZABLE_CLASS_NAME = "java.io.Serializable";

    private PsiClass clazz;
    private int index = -1;
    private Set<MemberSignature> nonPrivateConstructors;
    private Set<MemberSignature> nonPrivateMethods;
    private Set<MemberSignature> nonPrivateFields;
    private List<MemberSignature> staticInitializers;
    private boolean assertStatement = false;
    private boolean classObjectAccessExpression = false;
    private Map<PsiElement, String> memberMap =
            new HashMap<PsiElement, String>();

    private static final Comparator<PsiClass> INTERFACE_COMPARATOR =
            new Comparator<PsiClass>(){
        public int compare(PsiClass object1, PsiClass object2){
            if(object1 == null && object2 == null)
            {
                return 0;
            }if(object1 == null)
            {
                return 1;
            }
            if(object2 == null)
            {
                return -1;
            }
            final String name1 = object1.getQualifiedName();
            final String name2 = object2.getQualifiedName();
            if(name1 == null && name2 == null){
                return 0;
            }
            if(name1 == null){
                return 1;
            }
            if(name2 == null){
                return -1;
            }
            return name1.compareTo(name2);
        }
    };
    @NonNls private static final String CLASS_ACCESS_METHOD_PREFIX = "class$";

    private SerialVersionUIDBuilder(PsiClass clazz){
        super();
        this.clazz = clazz;
        nonPrivateMethods = new HashSet<MemberSignature>();
        final PsiMethod[] methods = clazz.getMethods();
        for(final PsiMethod method : methods){
            if(!method.isConstructor() &&
                    !method.hasModifierProperty(PsiModifier.PRIVATE)){
                final MemberSignature methodSignature =
                        new MemberSignature(method);
                nonPrivateMethods.add(methodSignature);
            }
        }
        nonPrivateFields = new HashSet<MemberSignature>();
        final PsiField[] fields = clazz.getFields();
        for(final PsiField field : fields){
            if(!field.hasModifierProperty(PsiModifier.PRIVATE) ||
                    !(field.hasModifierProperty(PsiModifier.STATIC) ||
                            field.hasModifierProperty(PsiModifier.TRANSIENT))){
                final MemberSignature fieldSignature =
                        new MemberSignature(field);
                nonPrivateFields.add(fieldSignature);
            }
        }

        staticInitializers = new ArrayList<MemberSignature>();
        final PsiClassInitializer[] initializers = clazz.getInitializers();
        if(initializers.length > 0){
            for(final PsiClassInitializer initializer : initializers){
                final PsiModifierList modifierList =
                        initializer.getModifierList();
                if(modifierList.hasModifierProperty(PsiModifier.STATIC)){
                    final MemberSignature initializerSignature =
                            MemberSignature.getStaticInitializerMemberSignature();
                    staticInitializers.add(initializerSignature);
                    break;
                }
            }
        }
        if(staticInitializers.isEmpty()){
            final PsiField[] psiFields = clazz.getFields();
            for(final PsiField field : psiFields){
                if(hasStaticInitializer(field)){
                    final MemberSignature initializerSignature =
                            MemberSignature.getStaticInitializerMemberSignature();
                    staticInitializers.add(initializerSignature);
                    break;
                }
            }
        }

        nonPrivateConstructors = new HashSet<MemberSignature>();
        final PsiMethod[] constructors = clazz.getConstructors();
        if(constructors.length == 0 && !clazz.isInterface()){
            // generated empty constructor if no constructor is defined in the source
            final MemberSignature constructorSignature;
            if(clazz.hasModifierProperty(PsiModifier.PUBLIC)){
                constructorSignature = MemberSignature.getPublicConstructor();
            } else{
                constructorSignature = MemberSignature.getPackagePrivateConstructor();
            }
            nonPrivateConstructors.add(constructorSignature);
        }
        for(final PsiMethod constructor : constructors){
            if(!constructor.hasModifierProperty(PsiModifier.PRIVATE)){
                final MemberSignature constructorSignature =
                        new MemberSignature(constructor);
                nonPrivateConstructors.add(constructorSignature);
            }
        }
    }

    public static long computeDefaultSUID(PsiClass psiClass){
        final PsiManager manager = psiClass.getManager();
        final Project project = manager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiClass serializable = manager.findClass(SERIALIZABLE_CLASS_NAME,
                                                        scope);
        if(serializable == null){
            // no jdk defined for project.
            return -1L;
        }

        final boolean isSerializable = psiClass.isInheritor(serializable, true);
        if(!isSerializable){
            return 0L;
        }

        final SerialVersionUIDBuilder serialVersionUIDBuilder = new SerialVersionUIDBuilder(psiClass);
        try{
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

            final String className = psiClass.getQualifiedName();
            dataOutputStream.writeUTF(className);

            final PsiModifierList classModifierList = psiClass.getModifierList();
            int classModifiers = MemberSignature.calculateModifierBitmap(classModifierList);
            final MemberSignature[] methodSignatures =
                    serialVersionUIDBuilder.getNonPrivateMethodSignatures();
            if(psiClass.isInterface()){
                classModifiers |= Modifier.INTERFACE;
                if(methodSignatures.length == 0){
                    // interfaces were not marked abstract when they did't have methods in java 1.0
                    // For serialization compatibility the abstract modifier is ignored.
                    classModifiers &= ~Modifier.ABSTRACT;
                }
            }
            dataOutputStream.writeInt(classModifiers);

            final PsiClass[] interfaces = psiClass.getInterfaces();
            Arrays.sort(interfaces, INTERFACE_COMPARATOR);
            for(PsiClass aInterfaces : interfaces){
                final String name = aInterfaces.getQualifiedName();
                dataOutputStream.writeUTF(name);
            }

            final MemberSignature[] fields = serialVersionUIDBuilder.getNonPrivateFields();
            Arrays.sort(fields);
            for(final MemberSignature field : fields){
                dataOutputStream.writeUTF(field.getName());
                dataOutputStream.writeInt(field.getModifiers());
                dataOutputStream.writeUTF(field.getSignature());
            }

            final MemberSignature[] staticInitializers = serialVersionUIDBuilder.getStaticInitializers();
            for(final MemberSignature staticInitializer : staticInitializers){
                dataOutputStream.writeUTF(staticInitializer.getName());
                dataOutputStream.writeInt(staticInitializer.getModifiers());
                dataOutputStream.writeUTF(staticInitializer.getSignature());
            }

            final MemberSignature[] constructors = serialVersionUIDBuilder.getNonPrivateConstructors();
            Arrays.sort(constructors);
            for(final MemberSignature constructor : constructors){
                dataOutputStream.writeUTF(constructor.getName());
                dataOutputStream.writeInt(constructor.getModifiers());
                dataOutputStream.writeUTF(constructor.getSignature());
            }

            Arrays.sort(methodSignatures);
            for(final MemberSignature methodSignature : methodSignatures){
                dataOutputStream.writeUTF(methodSignature.getName());
                dataOutputStream.writeInt(methodSignature.getModifiers());
                dataOutputStream.writeUTF(methodSignature.getSignature());
            }

            dataOutputStream.flush();
            @NonNls final String algorithm = "SHA";
            final MessageDigest digest = MessageDigest.getInstance(algorithm);
            final byte[] digestBytes = digest.digest(byteArrayOutputStream.toByteArray());
            long serialVersionUID = 0L;
            for(int i = Math.min(digestBytes.length, 8) - 1; i >= 0; i--){
                serialVersionUID = serialVersionUID << 8 |
                        digestBytes[i] & 0xFF;
            }
            return serialVersionUID;
        }
        catch(IOException exception){
            final InternalError internalError = new InternalError(exception.getMessage());
            internalError.initCause(exception);
            throw internalError;
        }
        catch(NoSuchAlgorithmException exception){
            final SecurityException securityException = new SecurityException(exception.getMessage());
            securityException.initCause(exception);
            throw securityException;
        }
    }

    private void createClassObjectAccessSynthetics(PsiType type){
        if(!classObjectAccessExpression){
            final MemberSignature syntheticMethod =
                    MemberSignature.getClassAccessMethodMemberSignature();
            nonPrivateMethods.add(syntheticMethod);
        }
        PsiType unwrappedType = type;
        @NonNls final StringBuffer fieldNameBuffer;
        if(type instanceof PsiArrayType){
            fieldNameBuffer = new StringBuffer();
            fieldNameBuffer.append("array");
            while(unwrappedType instanceof PsiArrayType){
                final PsiArrayType arrayType = (PsiArrayType) unwrappedType;
                unwrappedType = arrayType.getComponentType();
                fieldNameBuffer.append('$');
            }
        } else{
            fieldNameBuffer = new StringBuffer(CLASS_ACCESS_METHOD_PREFIX);
        }
        if(unwrappedType instanceof PsiPrimitiveType){
            final PsiPrimitiveType primitiveType = (PsiPrimitiveType) unwrappedType;
            fieldNameBuffer.append(MemberSignature.createPrimitiveType(primitiveType));
        } else{
            final String text = unwrappedType.getCanonicalText().replace('.',
                                                                         '$');
            fieldNameBuffer.append(text);
        }
        final String fieldName = fieldNameBuffer.toString();
        final MemberSignature memberSignature =
                new MemberSignature(fieldName, Modifier.STATIC,
                                    "Ljava/lang/Class;");
        if(!nonPrivateFields.contains(memberSignature)){
            nonPrivateFields.add(memberSignature);
        }
        classObjectAccessExpression = true;
    }

    private String getAccessMethodIndex(PsiElement element){
        String cache = memberMap.get(element);
        if(cache == null){
            cache = String.valueOf(index);
            index++;
            memberMap.put(element, cache);
        }
        return cache;
    }

    public MemberSignature[] getNonPrivateConstructors(){
        init();
        return nonPrivateConstructors.toArray(new MemberSignature[nonPrivateConstructors.size()]);
    }

    public MemberSignature[] getNonPrivateFields(){
        init();
        return nonPrivateFields.toArray(new MemberSignature[nonPrivateFields.size()]);
    }

    public MemberSignature[] getNonPrivateMethodSignatures(){
        init();
        return nonPrivateMethods.toArray(new MemberSignature[nonPrivateMethods.size()]);
    }

    public MemberSignature[] getStaticInitializers(){
        init();
        return staticInitializers.toArray(new MemberSignature[staticInitializers.size()]);
    }

    private static boolean hasStaticInitializer(PsiField field){
        if(field.hasModifierProperty(PsiModifier.STATIC)){
            final PsiManager manager = field.getManager();
            final Project project = manager.getProject();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiExpression initializer = field.getInitializer();
            if(initializer == null){
                return false;
            }
            final PsiType fieldType = field.getType();
            final PsiType stringType = PsiType.getJavaLangString(manager,
                                                                 scope);
            if(field.hasModifierProperty(PsiModifier.FINAL) &&
                    (fieldType instanceof PsiPrimitiveType || fieldType
                            .equals(stringType))){
                return !PsiUtil.isConstantExpression(initializer);
            } else{
                return true;
            }
        }
        return false;
    }

    private void init(){
        if(index < 0){
            index = 0;
            clazz.acceptChildren(this);
        }
    }

    public void visitAssertStatement(PsiAssertStatement statement){
        super.visitAssertStatement(statement);
        if(assertStatement){
            return;
        }
        final MemberSignature memberSignature =
                MemberSignature.getAssertionsDisabledFieldMemberSignature();
        nonPrivateFields.add(memberSignature);
        final PsiManager manager = clazz.getManager();
        final PsiElementFactory factory = manager.getElementFactory();
        final PsiClassType classType = factory.createType(clazz);
        createClassObjectAccessSynthetics(classType);
        if(staticInitializers.isEmpty()){
            final MemberSignature initializerSignature =
                    MemberSignature.getStaticInitializerMemberSignature();
            staticInitializers.add(initializerSignature);
        }
        assertStatement = true;
    }

    public void visitClassObjectAccessExpression(
            PsiClassObjectAccessExpression expression){
        final PsiTypeElement operand = expression.getOperand();
        final PsiType type = operand.getType();
        if(!(type instanceof PsiPrimitiveType)){
            createClassObjectAccessSynthetics(type);
        }
        super.visitClassObjectAccessExpression(expression);
    }

    public void visitMethodCallExpression(
            @NotNull PsiMethodCallExpression methodCallExpression){
        // for navigating the psi tree in the order javac navigates its AST
        final PsiExpressionList argumentList =
                methodCallExpression.getArgumentList();
        final PsiExpression[] expressions = argumentList.getExpressions();
        for(final PsiExpression expression : expressions){
            expression.accept(this);
        }
        final PsiReferenceExpression methodExpression =
                methodCallExpression.getMethodExpression();
        methodExpression.accept(this);
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement reference){
        super.visitReferenceElement(reference);
        final PsiElement parentClass = ClassUtils.getContainingClass(reference);
        if(reference.getParent() instanceof PsiTypeElement){
            return;
        }
        final PsiElement element = reference.resolve();
        if(!(element instanceof PsiClass)){
            return;
        }
        final PsiClass elementParentClass =
                ClassUtils.getContainingClass(element);
        if(elementParentClass == null ||
                !elementParentClass.equals(clazz) ||
                element.equals(parentClass)){
            return;
        }
        final PsiClass innerClass = (PsiClass) element;
        if(!innerClass.hasModifierProperty(PsiModifier.PRIVATE)){
            return;
        }
        final PsiMethod[] constructors = innerClass.getConstructors();
        if(constructors.length == 0){
            getAccessMethodIndex(innerClass);
        }
    }

    public void visitReferenceExpression(
            @NotNull PsiReferenceExpression expression){
        super.visitReferenceExpression(expression);
        final PsiElement element = expression.resolve();
        final PsiElement elementParentClass =
                ClassUtils.getContainingClass(element);
        final PsiElement expressionParentClass =
                ClassUtils.getContainingClass(expression);
        if(expressionParentClass == null || expressionParentClass
                .equals(elementParentClass)){
            return;
        }
        PsiElement parentOfParentClass =
                ClassUtils.getContainingClass(expressionParentClass);
        while(parentOfParentClass != null &&
                !parentOfParentClass.equals(clazz)){
            if(!(expressionParentClass instanceof PsiAnonymousClass)){
                getAccessMethodIndex(expressionParentClass);
            }
            getAccessMethodIndex(parentOfParentClass);
            parentOfParentClass = ClassUtils.getContainingClass(parentOfParentClass);
        }
        if(element instanceof PsiField){
            final PsiField field = (PsiField) element;
            if(field.hasModifierProperty(PsiModifier.PRIVATE)){
                boolean isStatic = false;
                final PsiType type = field.getType();
                if(field.hasModifierProperty(PsiModifier.STATIC)){
                    if(field.hasModifierProperty(PsiModifier.FINAL) &&
                            type instanceof PsiPrimitiveType){
                        final PsiExpression initializer = field.getInitializer();
                        if(PsiUtil.isConstantExpression(initializer)){
                            return;
                        }
                    }
                    isStatic = true;
                }
                final String returnTypeSignature =
                        MemberSignature.createTypeSignature(type).replace('/',
                                                                          '.');
                final String className = clazz.getQualifiedName();
                final StringBuilder signatureBuffer = new StringBuilder("(");
                if(!isStatic){
                    signatureBuffer.append('L').append(className).append(';');
                }
                final String accessMethodIndex = getAccessMethodIndex(field);
                if(!field.getContainingClass().equals(clazz)){
                    return;
                }
                String name = null;
                final PsiElement parent = expression.getParent();
                if(parent instanceof PsiAssignmentExpression){
                    final PsiAssignmentExpression assignment = (PsiAssignmentExpression) parent;
                    if(assignment.getLExpression().equals(expression)){
                        name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                                "02";
                        signatureBuffer.append(returnTypeSignature);
                    }
                } else if(parent instanceof PsiPostfixExpression){
                    final PsiPostfixExpression postfixExpression =
                            (PsiPostfixExpression) parent;
                    final PsiJavaToken operationSign = postfixExpression.getOperationSign();
                    final IElementType tokenType = operationSign.getTokenType();
                    if(tokenType.equals(JavaTokenType.PLUSPLUS)){
                        name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                                "08";
                    } else if(tokenType.equals(JavaTokenType.MINUSMINUS)){
                        name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                                "10";
                    }
                } else if(parent instanceof PsiPrefixExpression){
                    final PsiPrefixExpression prefixExpression = (PsiPrefixExpression) parent;
                    final PsiJavaToken operationSign = prefixExpression.getOperationSign();
                    final IElementType tokenType = operationSign.getTokenType();
                    if(tokenType.equals(JavaTokenType.PLUSPLUS)){
                        name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                                "04";
                    } else if(tokenType.equals(JavaTokenType.MINUSMINUS)){
                        name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex +
                                "06";
                    }
                }
                if(name == null){
                    name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex + "00";
                }
                signatureBuffer.append(')').append(returnTypeSignature);
                final String signature = signatureBuffer.toString();
                final MemberSignature methodSignature =
                        new MemberSignature(name, Modifier.STATIC, signature);
                nonPrivateMethods.add(methodSignature);
            }
        } else if(element instanceof PsiMethod){
            final PsiMethod method = (PsiMethod) element;
            if(method.hasModifierProperty(PsiModifier.PRIVATE) &&
                    method.getContainingClass().equals(clazz)){
                final String signature;
                if(method.hasModifierProperty(PsiModifier.STATIC)){
                    signature =
                            MemberSignature.createMethodSignature(method)
                                    .replace('/', '.');
                } else{
                    final String returnTypeSignature =
                            MemberSignature.createTypeSignature(method.getReturnType())
                                    .replace('/', '.');
                    @NonNls final StringBuilder signatureBuffer =
                            new StringBuilder();
                    signatureBuffer.append("(L");
                    signatureBuffer.append(clazz.getQualifiedName())
                            .append(';');
                    final PsiParameter[] parameters = method.getParameterList()
                            .getParameters();
                    for(final PsiParameter parameter : parameters){
                        final PsiType type = parameter.getType();
                        final String typeSignature = MemberSignature.createTypeSignature(type)
                                .replace('/', '.');
                        signatureBuffer.append(typeSignature);
                    }
                    signatureBuffer.append(')');
                    signatureBuffer.append(returnTypeSignature);
                    signature = signatureBuffer.toString();
                }
                final String accessMethodIndex = getAccessMethodIndex(method);
                final MemberSignature methodSignature =
                        new MemberSignature(ACCESS_METHOD_NAME_PREFIX +
                                accessMethodIndex + "00",
                                            Modifier.STATIC, signature);
                nonPrivateMethods.add(methodSignature);
            }
        }
    }
}