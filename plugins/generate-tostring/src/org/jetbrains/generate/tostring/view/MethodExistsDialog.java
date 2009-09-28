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
package org.jetbrains.generate.tostring.view;

import com.intellij.openapi.ui.Messages;
import org.jetbrains.generate.tostring.config.CancelPolicy;
import org.jetbrains.generate.tostring.config.ConflictResolutionPolicy;
import org.jetbrains.generate.tostring.config.DuplicatePolicy;
import org.jetbrains.generate.tostring.config.ReplacePolicy;

import javax.swing.*;

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
        if (exit == JOptionPane.CLOSED_OPTION || exit == JOptionPane.CANCEL_OPTION) {
            return CancelPolicy.getInstance();
        } else if (exit == JOptionPane.YES_OPTION) {
            return ReplacePolicy.getInstance();
        } else if (exit == JOptionPane.NO_OPTION) {
            return DuplicatePolicy.getInstance();
        }

        throw new IllegalArgumentException("exit code [" + exit + "] from YesNoCancelDialog not supported");
    }

}
