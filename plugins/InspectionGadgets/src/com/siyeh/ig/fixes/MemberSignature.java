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

import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Modifier;

public class MemberSignature implements Comparable<MemberSignature>
{
    @NonNls private static final String CONSTRUCTOR_NAME = "<init>";
    @NonNls private static final String INITIALIZER_SIGNATURE = "()V";
    @NonNls private static final MemberSignature ASSERTIONS_DISABLED_FIELD =
            new MemberSignature("$assertionsDisabled", Modifier.STATIC | Modifier.FINAL, "Z");
    @NonNls private static final MemberSignature CLASS_ACCESS_METHOD =
            new MemberSignature("class$", Modifier.STATIC, "(Ljava.lang.String;)Ljava.lang.Class;");
    @NonNls private static final MemberSignature PACKAGE_PRIVATE_CONSTRUCTOR =
            new MemberSignature(CONSTRUCTOR_NAME, 0, INITIALIZER_SIGNATURE);
    @NonNls private static final MemberSignature PUBLIC_CONSTRUCTOR =
            new MemberSignature(CONSTRUCTOR_NAME, Modifier.PUBLIC, INITIALIZER_SIGNATURE);
    @NonNls private static final MemberSignature STATIC_INITIALIZER =
            new MemberSignature("<clinit>", Modifier.STATIC, INITIALIZER_SIGNATURE);

    private final int modifiers;
    private final String name;
    private final String signature;

    public MemberSignature(PsiField field)
    {
        super();
        modifiers = calculateModifierBitmap(field.getModifierList());
        name = field.getName();
        signature = createTypeSignature(field.getType());
    }

    public MemberSignature(PsiMethod method)
    {
        super();
        modifiers = calculateModifierBitmap(method.getModifierList());
        signature = createMethodSignature(method).replace('/', '.');
        if(method.isConstructor()){
            name = CONSTRUCTOR_NAME;
        } else{
            name = method.getName();
        }
    }

    public MemberSignature(String name, int modifiers, @NonNls String signature)
    {
        super();
        this.name = name;
        this.modifiers = modifiers;
        this.signature = signature;
    }

    public static int calculateModifierBitmap(PsiModifierList modifierList)
    {
        int modifiers = 0;
        if (modifierList.hasModifierProperty(PsiModifier.PUBLIC))
        {
            modifiers |= Modifier.PUBLIC;
        }
        if (modifierList.hasModifierProperty(PsiModifier.PRIVATE))
        {
            modifiers |= Modifier.PRIVATE;
        }
        if (modifierList.hasModifierProperty(PsiModifier.PROTECTED))
        {
            modifiers |= Modifier.PROTECTED;
        }
        if (modifierList.hasModifierProperty(PsiModifier.STATIC))
        {
            modifiers |= Modifier.STATIC;
        }
        if (modifierList.hasModifierProperty(PsiModifier.FINAL))
        {
            modifiers |= Modifier.FINAL;
        }
        if (modifierList.hasModifierProperty(PsiModifier.VOLATILE))
        {
            modifiers |= Modifier.VOLATILE;
        }
        if (modifierList.hasModifierProperty(PsiModifier.TRANSIENT))
        {
            modifiers |= Modifier.TRANSIENT;
        }
        if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT))
        {
            modifiers |= Modifier.ABSTRACT;
        }
        if (modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED))
        {
            modifiers |= Modifier.SYNCHRONIZED;
        }
        if (modifierList.hasModifierProperty(PsiModifier.NATIVE))
        {
            modifiers |= Modifier.NATIVE;
        }
        if (modifierList.hasModifierProperty(PsiModifier.STRICTFP))
        {
            modifiers |= Modifier.STRICT;
        }
        return modifiers;
    }

    public int compareTo(MemberSignature other)
    {
        final int result = name.compareTo(other.name);
        if (result != 0)
        {
            return result;
        }
        return  signature.compareTo(other.signature);
    }

    public static String createMethodSignature(PsiMethod method)
    {
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        final StringBuilder signatureBuffer = new StringBuilder();
        signatureBuffer.append('(');
        for(final PsiParameter parameter : parameters){
            final PsiType type = parameter.getType();
            signatureBuffer.append(createTypeSignature(type));
        }
        signatureBuffer.append(')');
        final PsiType returnType = method.getReturnType();
        final String returnTypeSignature;
        if (returnType == null)
        {
            // constructors have void return type.
            returnTypeSignature = createTypeSignature(PsiType.VOID);
        }
        else
        {
            returnTypeSignature = createTypeSignature(returnType);
        }
        signatureBuffer.append(returnTypeSignature);
        return signatureBuffer.toString();
    }

    public static String createPrimitiveType(PsiPrimitiveType primitiveType)
    {
        @NonNls final String primitypeTypeSignature;
        if (primitiveType.equals(PsiType.INT))
        {
            primitypeTypeSignature = "I";
        }
        else if (primitiveType.equals(PsiType.BYTE))
        {
            primitypeTypeSignature = "B";
        }
        else if (primitiveType.equals(PsiType.LONG))
        {
            primitypeTypeSignature = "J";
        }
        else if (primitiveType.equals(PsiType.FLOAT))
        {
            primitypeTypeSignature = "F";
        }
        else if (primitiveType.equals(PsiType.DOUBLE))
        {
            primitypeTypeSignature = "D";
        }
        else if (primitiveType.equals(PsiType.SHORT))
        {
            primitypeTypeSignature = "S";
        }
        else if (primitiveType.equals(PsiType.CHAR))
        {
            primitypeTypeSignature = "C";
        }
        else if (primitiveType.equals(PsiType.BOOLEAN))
        {
            primitypeTypeSignature = "Z";
        }
        else if (primitiveType.equals(PsiType.VOID))
        {
            primitypeTypeSignature = "V";
        }
        else
        {
            throw new InternalError();
        }
        return primitypeTypeSignature;
    }

    public static String createTypeSignature(PsiType type)
    {
        final StringBuilder buffer = new StringBuilder();
        PsiType internalType = type;
        while (internalType instanceof PsiArrayType)
        {
            buffer.append('[');
            final PsiArrayType arrayType = (PsiArrayType)internalType;
            internalType = arrayType.getComponentType();
        }
        if (internalType instanceof PsiPrimitiveType)
        {
            final PsiPrimitiveType primitiveType = (PsiPrimitiveType)internalType;
            final String primitypeTypeSignature = createPrimitiveType(primitiveType);
            buffer.append(primitypeTypeSignature);
        }
        else
        {
            buffer.append('L');
            if (internalType instanceof PsiClassType)
            {
                final PsiClassType classType = (PsiClassType)internalType;
                PsiClass psiClass = classType.resolve();
                if (psiClass != null)
                {
                    final StringBuffer postFix = new StringBuffer("");
                    PsiClass containingClass = psiClass.getContainingClass();
                    while (containingClass != null)
                    {
                        // construct name for inner classes
                        postFix.insert(0, psiClass.getName()).insert(0, '$');
                        psiClass = containingClass;
                        containingClass = psiClass.getContainingClass();
                    }
                    final String qualifiedName = psiClass.getQualifiedName();
                    if(qualifiedName == null)
                    {
                        // for type parameters
                        buffer.append("java.lang.Object");
                    }
                    else
                    {
                        buffer.append(qualifiedName.replace('.', '/'));
                        buffer.append(postFix);
                    }
                }
            }
            else
            {
                // todo test this code path
                buffer.append(internalType.getCanonicalText().replace('.', '/'));
            }
            buffer.append(';');
        }
        return buffer.toString();
    }

    public boolean equals(Object object)
    {
        try {
            final MemberSignature other = (MemberSignature)object;
            return name.equals(other.name) &&
                    signature.equals(other.signature) &&
                    modifiers == other.modifiers;
        } catch (ClassCastException e) {
            return false;
        } catch (NullPointerException e) {
            return false;
        }
    }

    public static MemberSignature getAssertionsDisabledFieldMemberSignature()
    {
        return ASSERTIONS_DISABLED_FIELD;
    }

    public static MemberSignature getClassAccessMethodMemberSignature()
    {
        return CLASS_ACCESS_METHOD;
    }


    public int getModifiers()
    {
        return modifiers;
    }

    public String getName()
    {
        return name;
    }

    public static MemberSignature getPackagePrivateConstructor()
    {
        return PACKAGE_PRIVATE_CONSTRUCTOR;
    }

    public static MemberSignature getPublicConstructor()
    {
        return PUBLIC_CONSTRUCTOR;
    }

    public String getSignature()
    {
        return signature;
    }

    public static MemberSignature getStaticInitializerMemberSignature()
    {
        return STATIC_INITIALIZER;
    }

    public int hashCode()
    {
        return name.hashCode() + signature.hashCode();
    }

    public String toString()
    {
        return name + signature;
    }
}