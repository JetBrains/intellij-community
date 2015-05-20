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
package org.jetbrains.java.generate.view;

import com.intellij.openapi.ui.Messages;
import org.jetbrains.java.generate.config.CancelPolicy;
import org.jetbrains.java.generate.config.ConflictResolutionPolicy;
import org.jetbrains.java.generate.config.DuplicatePolicy;
import org.jetbrains.java.generate.config.ReplacePolicy;

/**
 * This is a dialog when the <code>toString()</code> method already exists.
 * <p/>
 * The user now has the choices to either:
 * <ul>
 *    <li/>Replace existing method
 *    <li/>Create a duplicate method
 *    <li/>Cancel
 * </ul>
 */
public class MethodExistsDialog {
    private MethodExistsDialog() {
    }

    /**
     * Shows this dialog.
     * <p/>
     * The user now has the choices to either:
     * <ul>
     *    <li/>Replace existing method
     *    <li/>Create a duplicate method
     *    <li/>Cancel
     * </ul>
     *
     * @param targetMethodName   the name of the target method (toString)
     * @return the chosen conflict resolution policy (never null)
     */
    public static ConflictResolutionPolicy showDialog(String targetMethodName) {
        int exit = Messages.showYesNoCancelDialog("Replace existing " + targetMethodName + " method", "Method Already Exists", Messages.getQuestionIcon());
        if (exit == Messages.CANCEL) {
            return CancelPolicy.getInstance();
        }
        if (exit == Messages.YES) {
            return ReplacePolicy.getInstance();
        }
        if (exit == Messages.NO) {
            return DuplicatePolicy.getInstance();
        }

        throw new IllegalArgumentException("exit code [" + exit + "] from YesNoCancelDialog not supported");
    }

}
