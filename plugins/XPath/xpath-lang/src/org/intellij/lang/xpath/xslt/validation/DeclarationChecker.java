/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.validation;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.quickfix.RenameVariableFix;
import org.intellij.lang.xpath.xslt.quickfix.AbstractFix;
import org.intellij.lang.xpath.xslt.util.ElementProcessor;

// TODO: include/import semantics are not 100% correct currently
public final class DeclarationChecker extends ElementProcessor<XmlTag> {
    boolean myContinue = true;
    private final boolean myVar;
    private final String myValue;
    private final PsiElement myToken;
    private final ProblemsHolder myProblemsHolder;
    private final boolean myOnTheFly;

    public DeclarationChecker(boolean var, XmlTag tag, String value, ProblemsHolder problemsHolder, boolean onTheFly) {
        super(tag);
        myVar = var;
        myValue = value;
        myProblemsHolder = problemsHolder;
        myOnTheFly = onTheFly;
        final XmlAttribute attribute = tag.getAttribute("name", null);
        assert attribute != null;
        myToken = XsltSupport.getAttValueToken(attribute);
    }

    protected boolean followImport() {
        return false;
    }

    protected void processTemplate(XmlTag tag) {
        if (!myVar) {
            processTag(tag);
        }
    }

    protected void processVarOrParam(XmlTag tag) {
        if (myVar) {
            processTag(tag);
        }
    }

    protected boolean shouldContinue() {
        return myContinue;
    }

    private void processTag(XmlTag t) {
        if (t != myRoot) {
            if (myValue.equals(t.getAttributeValue("name"))) {
                if (t.getParent() == myRoot.getParent() || !myVar || isInclude()) {
                    myProblemsHolder.registerProblem(myToken, "Duplicate declaration");
                } else {
                    final String innerKind = XsltSupport.isParam(myRoot) ? "Parameter" : "Variable";
                    final String outerKind = XsltSupport.isParam(t) ? "parameter" : "variable";

                    final LocalQuickFix fix1 = new RenameVariableFix(myRoot, "local").createQuickFix(myOnTheFly);
                    final LocalQuickFix fix2 = new RenameVariableFix(t, "outer").createQuickFix(myOnTheFly);

                    myProblemsHolder.registerProblem(myToken,
                            innerKind + " '" + myValue + "' shadows " + outerKind + " in outer scope",
                            AbstractFix.createFixes(fix1, fix2));
                }
                myContinue = false;
            }
        }
    }
}
