/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.generate.tostring;

import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.generate.tostring.exception.GenerateCodeException;
import org.jetbrains.generate.tostring.exception.PluginException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GenerationUtil {
  private static final Logger log = Logger.getInstance("#org.jetbrains.generate.tostring.GenerationUtil");

  /**
     * Handles any exception during the executing on this plugin.
     *
     * @param project PSI project
     * @param e       the caused exception.
     * @throws RuntimeException is thrown for severe exceptions
     */
    public static void handleException(Project project, Exception e) throws RuntimeException {
        log.info(e);

        if (e instanceof GenerateCodeException) {
            // code generation error - display velocity error in error dialog so user can identify problem quicker
            Messages.showMessageDialog(project,
                                       "Velocity error generating code - see IDEA log for more details (stacktrace should be in idea.log):\n" +
                                       e.getMessage(), "Warning", Messages.getWarningIcon());
        } else if (e instanceof PluginException) {
            // plugin related error - could be recoverable.
            Messages.showMessageDialog(project, "A PluginException was thrown while performing the action - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(), "Warning", Messages.getWarningIcon());
        } else if (e instanceof RuntimeException) {
            // unknown error (such as NPE) - not recoverable
            Messages.showMessageDialog(project, "An unrecoverable exception was thrown while performing the action - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(), "Error", Messages.getErrorIcon());
            throw (RuntimeException) e; // throw to make IDEA alert user
        } else {
            // unknown error (such as NPE) - not recoverable
            Messages.showMessageDialog(project, "An unrecoverable exception was thrown while performing the action - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(), "Error", Messages.getErrorIcon());
            throw new RuntimeException(e); // rethrow as runtime to make IDEA alert user
        }
    }

  /**
   * Combines the two lists into one list of members.
   *
   * @param filteredFields  fields to be included in the dialog
   * @param filteredMethods methods to be included in the dialog
   * @return the combined list
   */
  public static PsiElementClassMember[] combineToClassMemberList(PsiField[] filteredFields, PsiMethod[] filteredMethods) {
      PsiElementClassMember[] members = new PsiElementClassMember[filteredFields.length + filteredMethods.length];

      // first add fields
      for (int i = 0; i < filteredFields.length; i++) {
          members[i] = new PsiFieldMember(filteredFields[i]);
      }

      // then add methods
      for (int i = 0; i < filteredMethods.length; i++) {
          members[filteredFields.length + i] = new PsiMethodMember(filteredMethods[i]);
      }

      return members;
  }

  /**
   * Converts the list of {@link com.intellij.codeInsight.generation.PsiElementClassMember} to {PsiMember} objects.
   *
   * @param classMemberList  list of {@link com.intellij.codeInsight.generation.PsiElementClassMember}
   * @return a list of {PsiMember} objects.
   */
  public static List<PsiMember> convertClassMembersToPsiMembers(@Nullable List<PsiElementClassMember> classMemberList) {
      if (classMemberList == null || classMemberList.isEmpty()) {
        return Collections.emptyList();
      }
      List<PsiMember> psiMemberList = new ArrayList<PsiMember>();

      for (PsiElementClassMember classMember : classMemberList) {
          psiMemberList.add(classMember.getElement());
      }

      return psiMemberList;
  }
}