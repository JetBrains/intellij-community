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
package org.jetbrains.generate.tostring.inspection;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.generate.tostring.GenerateToStringContext;
import org.jetbrains.generate.tostring.GenerateToStringUtils;
import org.jetbrains.generate.tostring.psi.PsiAdapter;
import org.jetbrains.generate.tostring.psi.PsiAdapterFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Intention to check if the current class toString() method is out of
 * sync with the fields defined.
 * <p/>
 * This inspection will use filter information from the settings to exclude certain fields (eg. constants etc.).
 * <p/>
 * This inspection will only perform inspection if the class has a toString() method.
 */
public class FieldNotUsedInToStringInspection extends AbstractToStringInspection {

    private AbstractGenerateToStringQuickFix  fix = new FieldNotUsedInToStringQuickFix();

    public String getDisplayName() {
        return "Field not used in toString() method";
    }

    public String getShortName() {
        return "FieldNotUsedInToString";
    }

    public ProblemDescriptor[] checkClass(PsiClass clazz, InspectionManager im, boolean onTheFly) {
        // must be enabled to do check on the fly
        if (onTheFly && ! onTheFlyEnabled()) {
            return null;
        }

        List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
        checkFields(problems, clazz, im, onTheFly);
        checkMethods(problems, clazz, im, onTheFly);

        // any problems?
        if (problems.size() > 0) {
            if (log.isDebugEnabled()) log.debug("Number of problems found: " + problems.size());
            return problems.toArray(new ProblemDescriptor[problems.size()]);
        } else {
            log.debug("No problems found");
            return null; // no problems
        }
    }

    /**
     * Checking for problems with fields.
     *
     * @param problems   list of ProblemDescription found is added to this list
     * @param clazz      the class to check
     * @param im         InspectionManager
     * @param onTheFly   is on-the-fly enabled
     */
    private void checkFields(List<ProblemDescriptor> problems, PsiClass clazz, InspectionManager im, boolean onTheFly) {
        if (log.isDebugEnabled()) log.debug("checkFields: clazz=" + clazz + ", onTheFly=" + onTheFly);

        // must be a class
        if (clazz == null || clazz.getName() == null) {
            return;
        }

        // must have fields
        PsiAdapter psi = PsiAdapterFactory.getPsiAdapter();
        PsiField[] fields = psi.getFields(clazz);
        if (fields.length == 0) {
            log.debug("Class does not have any fields");
            return;
        }

        // a toString method must exist
        PsiMethod toStringMethod = psi.findMethodByName(clazz, "toString");
        if (toStringMethod == null) {
            log.debug("No toString() method");
            return;
        }

        // a toString code block must exist
        PsiCodeBlock code = toStringMethod.getBody();
        if (code == null) {
            log.debug("No toString() code");
            return;
        }

        // check if toString uses reflection if so exit
        String body = code.getText();
        if (body.indexOf("getDeclaredFields()") != -1) {
            log.debug("Using reflection");
            return;
        } else if (body.indexOf("ReflectionToStringBuilder(this).toString()") != -1) {
            log.debug("Using reflection (ReflectionToStringBuilder)");
            return;
        }

        // get list of fields supposed to be dumped in the toString method
        Project project = im.getProject();
        PsiManager manager = psi.getPsiManager(project);
        fields = GenerateToStringUtils.filterAvailableFields(project, psi, clazz, GenerateToStringContext.getConfig().getFilterPattern());
        if (fields == null || fields.length == 0) {
            log.debug("No fields to be dumped as all fields was excluded (exclude field by XXX from Settings)");
            return;
        }

        // toString exists and fields are supposed to be dumped
        // check if any fields are missing (out of sync)
        for (PsiField field : fields) {
            if (log.isDebugEnabled()) log.debug("Evaluating if field " + field.getName() + " is in toString() method");

            // field must be enclosed with non words before and after the field to ensure the fieldname are dumped
            String pattern = "(?s).*\\W" + field.getName() + "[\\W&&[^=]].*";
            if (log.isDebugEnabled()) log.debug("Match pattern = " + pattern);

            // use regexp to match if field is used in code
            if (!body.matches(pattern)) {
                if (log.isDebugEnabled()) log.debug("Field is not used in toString() method (out-of-sync): " + field);
                ProblemDescriptor problem = im.createProblemDescriptor(field, "Field '" + field.getName() + "' is not used in toString() method", fix, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
                problems.add(problem);
            }
        }

    }

    /**
     * Checking for problems with fields.
     *
     * @param problems   list of ProblemDescription found is added to this list
     * @param clazz      the class to check
     * @param im         InspectionManager
     * @param onTheFly   is on-the-fly enabled
     */
    private void checkMethods(List<ProblemDescriptor> problems, PsiClass clazz, InspectionManager im, boolean onTheFly) {
        if (log.isDebugEnabled()) log.debug("checkMethods: clazz=" + clazz + ", onTheFly=" + onTheFly);

        // must be a class
        if (clazz == null || clazz.getName() == null) {
            return;
        }

        // must have 'Enable getters in code generation' set to true
        if (! GenerateToStringContext.getConfig().isEnableMethods()) {
            return;
        }

        // a toString method must exist
        PsiAdapter psi = PsiAdapterFactory.getPsiAdapter();
        PsiMethod toStringMethod = psi.findMethodByName(clazz, "toString");
        if (toStringMethod == null) {
            log.debug("No toString() method");
            return;
        }

        // a toString code block must exist
        PsiCodeBlock code = toStringMethod.getBody();
        if (code == null) {
            log.debug("No toString() code");
            return;
        }

        // check if toString uses reflection if so exit
        String body = code.getText();
        if (body.indexOf("getDeclaredFields()") != -1) {
            log.debug("Using reflection");
            return;
        }

        // must have methods
        PsiMethod[] methods = psi.getMethods(clazz);
        if (methods.length == 0) {
            log.debug("Class does not have any methods");
            return;
        }

        // get list of methods supposed to be dumped in the toString method
        Project project = im.getProject();
        methods = GenerateToStringUtils.filterAvailableMethods(psi, clazz, GenerateToStringContext.getConfig().getFilterPattern());
        if (methods == null || methods.length == 0) {
            log.debug("No getter methods to be dumped as all methods was excluded or a field existed for the getter method (exclude method by XXX from Settings)");
            return;
        }

        // toString exists and methods are supposed to be dumped
        // check if any methods are missing (out of sync)
        for (PsiMethod method : methods) {
            if (log.isDebugEnabled()) log.debug("Evaluating if method " + method.getName() + " is in toString() method");

            // method must be enclosed with non words before and after the method to ensure the fieldname are dumped
            String pattern = "(?s).*\\W" + method.getName() + "[\\W&&[^=]].*";
            if (log.isDebugEnabled()) log.debug("Match pattern = " + pattern);

            // use regexp to match if method is used in code
            if (!body.matches(pattern)) {
                // method is not in toString
                if (log.isDebugEnabled()) log.debug("Getter method is not used in toString() method (out-of-sync): " + method);
                ProblemDescriptor problem = im.createProblemDescriptor(method, "Method '" + method.getName() + "' is not used in toString() method", fix, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
                problems.add(problem);
            }
        }

    }


/*
    public static void main(String[] args) {
        // for testing regexp pattern
        String me = "return \"DummyTestBean{\" +\n" +
                "                \", myNewString ='\" + myNewString + \"'\" +\n" +
                "                \"}\";";
        System.out.println("me = " + me);
        System.out.println( me.matches("(?s).*\\WmyNewString[\\W*&&[^\\s*=]].*") );
    }
*/

}
