package com.siyeh.ig.fixes;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * @author <A href="bas@carp-technologies.nl">Bas Leijdekkers</a>
 */
public class SerialVersionUIDBuilder extends PsiRecursiveElementVisitor
{
    private static final String ACCESS_METHOD_NAME_PREFIX = "access$";

    private static final String SERIALIZABLE_CLASS_NAME = "java.io.Serializable";

    private PsiClass clazz;
    private int index = -1;
    private Set nonPrivateConstructors;
    private Set nonPrivateMethods;
    private Set nonPrivateFields;
    private List staticInitializers;
    private boolean assertStatement = false;
    private boolean classObjectAccessExpression = false;
    private Map memberMap = new HashMap();

    private static final Comparator INTERFACE_COMPARATOR = new Comparator()
    {
        public int compare(Object object1, Object object2)
        {
            final String name1 = ((PsiClass)object1).getQualifiedName();
            final String name2 = ((PsiClass)object2).getQualifiedName();
            return name1.compareTo(name2);
        }
    };
    private static final String CLASS_ACCESS_METHOD_PREFIX = "class$";

    private SerialVersionUIDBuilder(PsiClass clazz)
    {
        this.clazz = clazz;
        nonPrivateMethods = new HashSet();
        final PsiMethod[] methods = clazz.getMethods();
        for (int i = 0; i < methods.length; i++)
        {
            final PsiMethod method = methods[i];
            if (!method.isConstructor() && !method.hasModifierProperty(PsiModifier.PRIVATE))
            {
                final MemberSignature methodSignature = new MemberSignature(method);
                nonPrivateMethods.add(methodSignature);
            }
        }
        nonPrivateFields = new HashSet();
        final PsiField[] fields = clazz.getFields();
        for (int i = 0; i < fields.length; i++)
        {
            final PsiField field = fields[i];
            if (!field.hasModifierProperty(PsiModifier.PRIVATE) ||
                !(field.hasModifierProperty(PsiModifier.STATIC) |
                  field.hasModifierProperty(PsiModifier.TRANSIENT)))
            {
                final MemberSignature fieldSignature = new MemberSignature(field);
                nonPrivateFields.add(fieldSignature);
            }
        }

        staticInitializers = new ArrayList();
        final PsiClassInitializer[] initializers = clazz.getInitializers();
        if (initializers.length > 0)
        {
            for (int i = 0; i < initializers.length; i++)
            {
                final PsiClassInitializer initializer = initializers[i];
                final PsiModifierList modifierList = initializer.getModifierList();
                if (modifierList.hasModifierProperty(PsiModifier.STATIC))
                {
                    final MemberSignature initializerSignature =
                            MemberSignature.getStaticInitializerMemberSignature();
                    staticInitializers.add(initializerSignature);
                    break;
                }
            }
        }
        if (staticInitializers.isEmpty())
        {
            final PsiField[] psiFields = clazz.getFields();
            for (int i = 0; i < psiFields.length; i++)
            {
                final PsiField field = psiFields[i];
                if (hasStaticInitializer(field))
                {
                    final MemberSignature initializerSignature =
                            MemberSignature.getStaticInitializerMemberSignature();
                    staticInitializers.add(initializerSignature);
                    break;
                }
            }
        }

        nonPrivateConstructors = new HashSet();
        final PsiMethod[] constructors = clazz.getConstructors();
        if (constructors.length == 0 && !clazz.isInterface())
        {
            // generated empty constructor if no constructor is defined in the source
            final MemberSignature constructorSignature;
            if (clazz.hasModifierProperty(PsiModifier.PUBLIC))
            {
                constructorSignature = MemberSignature.getPublicConstructor();
            }
            else
            {
                constructorSignature = MemberSignature.getPackagePrivateConstructor();
            }
            nonPrivateConstructors.add(constructorSignature);
        }
        for (int i = 0; i < constructors.length; i++)
        {
            final PsiMethod constructor = constructors[i];
            if (!constructor.hasModifierProperty(PsiModifier.PRIVATE))
            {
                final MemberSignature constructorSignature = new MemberSignature(constructor);
                nonPrivateConstructors.add(constructorSignature);
            }
        }
    }

    public static long computeDefaultSUID(PsiClass psiClass)
    {
        final PsiManager manager = psiClass.getManager();
        final Project project = manager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiClass serializable = manager.findClass(SERIALIZABLE_CLASS_NAME, scope);
        if (serializable == null)
        {
            // no jdk defined for project.
            return -1L;
        }

        final boolean isSerializable = psiClass.isInheritor(serializable, true);
        if (!isSerializable)
        {
            return 0L;
        }

        final SerialVersionUIDBuilder serialVersionUIDBuilder = new SerialVersionUIDBuilder(psiClass);
        try
        {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

            final String className = psiClass.getQualifiedName();
            dataOutputStream.writeUTF(className);

            final PsiModifierList classModifierList = psiClass.getModifierList();
            int classModifiers = MemberSignature.calculateModifierBitmap(classModifierList);
            final MemberSignature[] methodSignatures =
                    serialVersionUIDBuilder.getNonPrivateMethodSignatures();
            if (psiClass.isInterface())
            {
                classModifiers |= Modifier.INTERFACE;
                if (methodSignatures.length ==0)
                {
                    // interfaces were not marked abstract when they did't have methods in java 1.0
                    // For serialization compatibility the abstract modifier is ignored.
                    classModifiers &= ~Modifier.ABSTRACT;
                }
            }
            dataOutputStream.writeInt(classModifiers);

            final PsiClass[] interfaces = psiClass.getInterfaces();
            Arrays.sort(interfaces, INTERFACE_COMPARATOR);
            for (int i = 0; i < interfaces.length; i++)
            {
                final String name = interfaces[i].getQualifiedName();
                dataOutputStream.writeUTF(name);
            }

            final MemberSignature[] fields = serialVersionUIDBuilder.getNonPrivateFields();
            Arrays.sort(fields);
            for (int i = 0; i < fields.length; i++)
            {
                final MemberSignature field = fields[i];
                dataOutputStream.writeUTF(field.getName());
                dataOutputStream.writeInt(field.getModifiers());
                dataOutputStream.writeUTF(field.getSignature());

            }

            final MemberSignature[] staticInitializers = serialVersionUIDBuilder.getStaticInitializers();
            for (int i = 0; i < staticInitializers.length; i++)
            {
                final MemberSignature staticInitializer = staticInitializers[i];
                dataOutputStream.writeUTF(staticInitializer.getName());
                dataOutputStream.writeInt(staticInitializer.getModifiers());
                dataOutputStream.writeUTF(staticInitializer.getSignature());
            }


            final MemberSignature[] constructors = serialVersionUIDBuilder.getNonPrivateConstructors();
            Arrays.sort(constructors);
            for (int i = 0; i < constructors.length; i++)
            {
                final MemberSignature constructor = constructors[i];
                dataOutputStream.writeUTF(constructor.getName());
                dataOutputStream.writeInt(constructor.getModifiers());
                dataOutputStream.writeUTF(constructor.getSignature());

            }

            Arrays.sort(methodSignatures);
            for (int i = 0; i < methodSignatures.length; i++)
            {
                final MemberSignature methodSignature = methodSignatures[i];

                dataOutputStream.writeUTF(methodSignature.getName());
                dataOutputStream.writeInt(methodSignature.getModifiers());
                dataOutputStream.writeUTF(methodSignature.getSignature());
            }

            dataOutputStream.flush();
            final MessageDigest digest = MessageDigest.getInstance("SHA");
            final byte[] digestBytes = digest.digest(byteArrayOutputStream.toByteArray());
            long serialVersionUID = 0L;
            for (int i = Math.min(digestBytes.length, 8) - 1; i >=0; i--)
            {
                serialVersionUID = serialVersionUID << 8 | digestBytes[i] & 0xFF;
            }
            return serialVersionUID;

        }
        catch(IOException exception)
        {
            final InternalError internalError = new InternalError(exception.getMessage());
            internalError.initCause(exception);
            throw internalError;
        }
        catch(NoSuchAlgorithmException exception)
        {
            final SecurityException securityException = new SecurityException(exception.getMessage());
            securityException.initCause(exception);
            throw securityException;
        }
    }

    private void createClassObjectAccessSynthetics(PsiType type)
    {
        if (!classObjectAccessExpression)
        {
            final MemberSignature syntheticMethod =
                    MemberSignature.getClassAccessMethodMemberSignature();
            nonPrivateMethods.add(syntheticMethod);
        }
        final StringBuffer fieldNameBuffer;
        PsiType unwrappedType = type;
        if (type instanceof PsiArrayType)
        {
            fieldNameBuffer = new StringBuffer("array");
            while (unwrappedType instanceof PsiArrayType)
            {
                final PsiArrayType arrayType = (PsiArrayType)unwrappedType;
                unwrappedType = arrayType.getComponentType();
                fieldNameBuffer.append("$");
            }

        }
        else
        {
            fieldNameBuffer = new StringBuffer(CLASS_ACCESS_METHOD_PREFIX);
        }
        if (unwrappedType instanceof PsiPrimitiveType)
        {
            final PsiPrimitiveType primitiveType = (PsiPrimitiveType)unwrappedType;
            fieldNameBuffer.append(MemberSignature.createPrimitiveType(primitiveType));
        }
        else
        {
            final String text = unwrappedType.getCanonicalText().replace('.', '$');
            fieldNameBuffer.append(text);
        }
        final String fieldName = fieldNameBuffer.toString();
        final MemberSignature memberSignature =
                new MemberSignature(fieldName, Modifier.STATIC, "Ljava/lang/Class;");
        if (!nonPrivateFields.contains(memberSignature))
        {
            nonPrivateFields.add(memberSignature);
        }
        classObjectAccessExpression = true;
    }

    private String getAccessMethodIndex(PsiElement element)
    {
        String cache = (String)memberMap.get(element);
        if (cache == null)
        {
            cache = String.valueOf(index);
            index++;
            memberMap.put(element, cache);
        }
        return cache;
    }

    public MemberSignature[] getNonPrivateConstructors()
    {
        init();
        return (MemberSignature[])
        nonPrivateConstructors.toArray(new MemberSignature[nonPrivateConstructors.size()]);
    }

    public MemberSignature[] getNonPrivateFields()
    {
        init();
        return (MemberSignature[])
        nonPrivateFields.toArray(new MemberSignature[nonPrivateFields.size()]);
        // todo need inspection for toArray method
        // wrong example:  list1.toArary(new Object[array2.size()]);
    }

    public MemberSignature[] getNonPrivateMethodSignatures()
    {
        init();
        return (MemberSignature[])
        nonPrivateMethods.toArray(new MemberSignature[nonPrivateMethods.size()]);
    }

    public MemberSignature[] getStaticInitializers()
    {
        init();
        return (MemberSignature[])
        staticInitializers.toArray(new MemberSignature[staticInitializers.size()]);
    }

    private static boolean hasStaticInitializer(PsiField field)
    {
        if (field.hasModifierProperty(PsiModifier.STATIC))
        {
            final PsiManager manager = field.getManager();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(manager.getProject());
            final PsiExpression initializer = field.getInitializer();
            if (initializer == null)
            {
                return false;
            }
            final PsiType fieldType = field.getType();
            final PsiType stringType = PsiType.getJavaLangString(manager, scope);
            if (field.hasModifierProperty(PsiModifier.FINAL) &&
                (fieldType instanceof PsiPrimitiveType || fieldType.equals(stringType)))
            {
                return !PsiUtil.isConstantExpression(initializer);
            }
            else
            {
                return true;
            }
        }
        return false;
    }


    private void init()
    {
        if (index < 0)
        {
            index = 0;
            clazz.acceptChildren(this);
        }
    }

    public void visitAssertStatement(PsiAssertStatement statement)
    {
        super.visitAssertStatement(statement);
        if (assertStatement)
        {
            return;
        }
        final MemberSignature memberSignature =
                MemberSignature.getAssertionsDisabledFieldMemberSignature();
        nonPrivateFields.add(memberSignature);
        final PsiManager manager = clazz.getManager();
        final PsiElementFactory factory = manager.getElementFactory();
        final PsiClassType classType = factory.createType(clazz);
        createClassObjectAccessSynthetics(classType);
        if (staticInitializers.isEmpty())
        {
            final MemberSignature initializerSignature =
                    MemberSignature.getStaticInitializerMemberSignature();
            staticInitializers.add(initializerSignature);
        }
        assertStatement = true;

    }

    public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression)
    {
        final PsiTypeElement operand = expression.getOperand();
        final PsiType type = operand.getType();
        if (!(type instanceof PsiPrimitiveType))
        {
            createClassObjectAccessSynthetics(type);
        }
        super.visitClassObjectAccessExpression(expression);
    }

    public void visitMethodCallExpression(PsiMethodCallExpression methodCallExpression)
    {
        // for navigating the psi tree in the order javac navigates its AST
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        final PsiExpression[] expressions = argumentList.getExpressions();
        for (int i = 0; i < expressions.length; i++)
        {
            final PsiExpression expression = expressions[i];
            expression.accept(this);
        }
        final PsiReferenceExpression methodExpression =
                methodCallExpression.getMethodExpression();
        methodExpression.accept(this);
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement reference)
    {
        super.visitReferenceElement(reference);
        final PsiElement parentClass = PsiTreeUtil.getParentOfType(reference, PsiClass.class);
        if (reference.getParent() instanceof PsiTypeElement)
        {
            return;
        }
        final PsiElement element = reference.resolve();
        if (!(element instanceof PsiClass))
        {
            return;
        }
        final PsiClass elementParentClass =
                (PsiClass)PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (elementParentClass == null ||
            !elementParentClass.equals(clazz) ||
            element.equals(parentClass))
        {
            return;
        }
        final PsiClass innerClass = (PsiClass)element;
        if (!innerClass.hasModifierProperty(PsiModifier.PRIVATE))
        {
            return;
        }
        final PsiMethod[] constructors = innerClass.getConstructors();
        if (constructors.length == 0)
        {
            getAccessMethodIndex(innerClass);
        }
    }

    public void visitReferenceExpression(PsiReferenceExpression expression)
    {
        super.visitReferenceExpression(expression);
        final PsiElement element = expression.resolve();
        final PsiElement elementParentClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        final PsiElement expressionParentClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
        if (expressionParentClass == null || expressionParentClass.equals(elementParentClass))
        {
            return;
        }
        PsiElement parentOfParentClass = PsiTreeUtil.getParentOfType(expressionParentClass, PsiClass.class);
        while (parentOfParentClass != null && !parentOfParentClass.equals(clazz))
        {
            if (!(expressionParentClass instanceof PsiAnonymousClass))
            {
                getAccessMethodIndex(expressionParentClass);
            }
            getAccessMethodIndex(parentOfParentClass);
            parentOfParentClass = PsiTreeUtil.getParentOfType(parentOfParentClass, PsiClass.class);
        }
        if (element instanceof PsiField)
        {
            final PsiField field = (PsiField)element;
            if (field.hasModifierProperty(PsiModifier.PRIVATE))
            {
                boolean isStatic = false;
                final PsiType type = field.getType();
                if (field.hasModifierProperty(PsiModifier.STATIC))
                {
                    if (field.hasModifierProperty(PsiModifier.FINAL) &&
                                type instanceof PsiPrimitiveType)
                    {
                        final PsiExpression initializer = field.getInitializer();
                        if (PsiUtil.isConstantExpression(initializer))
                        {
                            return;
                        }
                    }
                    isStatic = true;
                }
                final String returnTypeSignature =
                        MemberSignature.createTypeSignature(type).replace('/', '.');
                final String className = clazz.getQualifiedName();
                final StringBuffer signatureBuffer = new StringBuffer("(");
                if (!isStatic)
                {
                    signatureBuffer.append('L').append(className).append(';');
                }
                final String accessMethodIndex = getAccessMethodIndex(field);
                if (!field.getContainingClass().equals(clazz))
                {
                    return;
                }
                String name = null;
                final PsiElement parent = expression.getParent();
                if (parent instanceof PsiAssignmentExpression)
                {
                    final PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
                    if (assignment.getLExpression().equals(expression))
                    {
                        name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex + "02";
                        signatureBuffer.append(returnTypeSignature);
                    }
                }
                else if (parent instanceof PsiPostfixExpression)
                {
                    final PsiPostfixExpression postfixExpression =
                            (PsiPostfixExpression)parent;
                    final PsiJavaToken operationSign = postfixExpression.getOperationSign();
                    final IElementType tokenType = operationSign.getTokenType();
                    if (tokenType.equals(JavaTokenType.PLUSPLUS))
                    {
                        name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex + "08";
                    }
                    else if (tokenType.equals(JavaTokenType.MINUSMINUS))
                    {
                        name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex + "10";
                    }
                }
                else if (parent instanceof PsiPrefixExpression)
                {
                    final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)parent;
                    final PsiJavaToken operationSign = prefixExpression.getOperationSign();
                    final IElementType tokenType = operationSign.getTokenType();
                    if (tokenType.equals(JavaTokenType.PLUSPLUS))
                    {
                        name =  ACCESS_METHOD_NAME_PREFIX + accessMethodIndex + "04";
                    }
                    else if (tokenType.equals(JavaTokenType.MINUSMINUS))
                    {
                        name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex + "06";
                    }
                }
                if (name == null)
                {
                    name = ACCESS_METHOD_NAME_PREFIX + accessMethodIndex + "00";
                }
                signatureBuffer.append(')').append(returnTypeSignature);
                final String signature = signatureBuffer.toString();
                final MemberSignature methodSignature =
                        new MemberSignature(name, Modifier.STATIC, signature);
                nonPrivateMethods.add(methodSignature);
            }
        }
        else if (element instanceof PsiMethod)
        {
            final PsiMethod method = (PsiMethod)element;
            if (method.hasModifierProperty(PsiModifier.PRIVATE) &&
                method.getContainingClass().equals(clazz))
            {
                final String signature;
                if (method.hasModifierProperty(PsiModifier.STATIC))
                {
                    signature =
                        MemberSignature.createMethodSignature(method).replace('/', '.');
                }
                else
                {
                    final String returnTypeSignature =
                            MemberSignature.createTypeSignature(method.getReturnType()).replace('/', '.');
                    final StringBuffer signatureBuffer = new StringBuffer("(L");
                    signatureBuffer.append(clazz.getQualifiedName()).append(';');
                    final PsiParameter[] parameters = method.getParameterList().getParameters();
                    for (int i = 0; i < parameters.length; i++)
                    {
                        final PsiParameter parameter = parameters[i];
                        final PsiType type = parameter.getType();
                        final String typeSignature = MemberSignature.createTypeSignature(type).replace('/', '.');
                        signatureBuffer.append(typeSignature);
                    }
                    signatureBuffer.append(')');
                    signatureBuffer.append(returnTypeSignature);
                    signature = signatureBuffer.toString();
                }
                final String accessMethodIndex = getAccessMethodIndex(method);
                final MemberSignature methodSignature =
                        new MemberSignature(ACCESS_METHOD_NAME_PREFIX + accessMethodIndex + "00",
                                Modifier.STATIC, signature);
                nonPrivateMethods.add(methodSignature);
            }

        }
    }
}