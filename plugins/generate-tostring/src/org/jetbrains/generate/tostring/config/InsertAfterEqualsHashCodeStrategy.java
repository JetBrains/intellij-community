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
package org.jetbrains.generate.tostring.config;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.generate.tostring.psi.PsiAdapter;
import org.jetbrains.generate.tostring.psi.PsiAdapterFactory;

/**
 * Inserts the method after the hashCode/equals methods in the javafile.
 */
public class InsertAfterEqualsHashCodeStrategy implements InsertNewMethodStrategy {

    private static final InsertAfterEqualsHashCodeStrategy instance = new InsertAfterEqualsHashCodeStrategy();
    private static PsiAdapter psi;

    private InsertAfterEqualsHashCodeStrategy() {
    }

    public static InsertAfterEqualsHashCodeStrategy getInstance() {
        return instance;
    }

    public boolean insertNewMethod(PsiClass clazz, PsiMethod newMethod, Editor editor) throws IncorrectOperationException {
        // lazy initialize otherwise IDEA throws error: Component requests are not allowed before they are created
        if (psi == null) {
            psi = PsiAdapterFactory.getPsiAdapter();
        }

        // if main method exists and is the last then add toString just before main method
        PsiMethod methodHashCode = psi.findHashCodeMethod(clazz);
        PsiMethod methodEquals = psi.findEqualsMethod(clazz);

        // if both methos exist determine the last method in the javafile
        PsiMethod method;
        if (methodEquals != null && methodHashCode != null) {
            if (methodEquals.getTextOffset() > methodHashCode.getTextOffset()) {
                method = methodEquals;
            } else {
                method = methodHashCode;
            }
        } else {
            method = methodHashCode != null ? methodHashCode : methodEquals;
        }

        if (method != null) {
            // insert after the equals/hashCode method
            clazz.addAfter(newMethod, method);
        } else {
            // no equals/hashCode so insert at caret
            InsertAtCaretStrategy.getInstance().insertNewMethod(clazz, newMethod, editor);
        }

        return true;
    }

    public String toString() {
        return "After equals/hashCode";
    }

}
