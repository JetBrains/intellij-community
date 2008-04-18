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
package org.intellij.lang.xpath.xslt.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.psi.XPathElement;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RefactoringUtil {
    private RefactoringUtil() {
    }

    public static Set<XPathExpression> collectMatchingExpressions(XPathExpression expression) {
        final PsiElement usageBlock = XsltCodeInsightUtil.getUsageBlock(expression);
        if (usageBlock == null) return Collections.emptySet();
        
        final ExpressionCollector visitor = new ExpressionCollector(expression);
        usageBlock.accept(visitor);

        return visitor.getMatches();
    }

    public static Set<XPathVariableReference> collectVariableReferences(PsiElement block) {
        final VariableReferenceCollector visitor = new VariableReferenceCollector(block.getProject());
        block.accept(visitor);

        return visitor.getMatches();
    }

    public static XmlTag addParameter(XmlTag templateTag, XmlTag paramTag) throws IncorrectOperationException {
        return addParameter(templateTag, paramTag, XsltCodeInsightUtil.findLastParam(templateTag));
    }

    public static XmlTag addParameter(XmlTag templateTag, XmlTag paramTag, XmlTag anchor) throws IncorrectOperationException {
        final PsiElement c;
        if (anchor != null) {
            paramTag = (XmlTag)templateTag.addAfter(paramTag, anchor);
        } else if ((c = XsltCodeInsightUtil.findFirstRealTagChild(templateTag)) != null) {
            paramTag = (XmlTag)templateTag.addBefore(paramTag, c);
        } else {
            paramTag = (XmlTag)templateTag.add(paramTag);
        }
        paramTag = (XmlTag)paramTag.getManager().getCodeStyleManager().reformat(paramTag);
        return paramTag;
    }

    public static XmlTag addWithParam(XmlTag templateTag) throws IncorrectOperationException {
        XmlTag withParamTag = templateTag.createChildTag("with-param", XsltSupport.XSLT_NS, null, false);

        final XmlTag anchor = XsltCodeInsightUtil.findLastWithParam(templateTag);
        final PsiElement c;
        if (anchor != null) {
            withParamTag = (XmlTag)templateTag.addAfter(withParamTag, anchor);
        } else if ((c = XsltCodeInsightUtil.findFirstRealTagChild(templateTag)) != null) {
            withParamTag = (XmlTag)templateTag.addBefore(withParamTag, c);
        } else {
            withParamTag = (XmlTag)templateTag.add(withParamTag);
        }
        withParamTag = (XmlTag)withParamTag.getManager().getCodeStyleManager().reformat(withParamTag);
        return withParamTag;
    }

    // TODO: verify
    static abstract class DeepXPathVistor extends XmlRecursiveElementVisitor {
        private final XsltSupport myXsltSupport;

        protected DeepXPathVistor(Project project) {
            myXsltSupport = XsltSupport.getInstance(project);
        }

        protected void superVisitElement(PsiElement element) {
            super.visitElement(element);
        }
        
        @Override
        public void visitElement(PsiElement element) {
            if (element instanceof XPathElement) {
                if (element instanceof XPathExpression) {
                    visitXPathExpression(((XPathExpression)element));
                }
                element.acceptChildren(this);
            } else {
                super.visitElement(element);
            }
        }

        protected abstract void visitXPathExpression(XPathExpression expr);

        public void visitXmlAttribute(XmlAttribute attribute) {
            if (XsltSupport.isXPathAttribute(attribute)) {
                final PsiFile[] xpathFiles = myXsltSupport.getFiles(attribute);
                for (PsiFile xpathFile : xpathFiles) {
                    xpathFile.accept(this);
                }
            }
        }
    }

    static class VariableReferenceCollector extends DeepXPathVistor {
        private Set<XPathVariableReference> myList;

        protected VariableReferenceCollector(Project project) {
            super(project);
            myList = new HashSet<XPathVariableReference>();
        }

        protected void visitXPathExpression(XPathExpression expr) {
            if (expr instanceof XPathVariableReference) {
                myList.add((XPathVariableReference)expr);
            }
        }

        public Set<XPathVariableReference> getMatches() {
            return myList;
        }
    }

    static class ExpressionCollector extends DeepXPathVistor {
        private final XPathExpression myExpression;
        private final Set<XPathExpression> myList;

        public ExpressionCollector(XPathExpression expression) {
            super(expression.getProject());
            myExpression = expression;
            myList = new HashSet<XPathExpression>();
        }

        protected void visitXPathExpression(XPathExpression expr) {
            if (expr != myExpression) {
                if (isAccepted(expr) && isEquivalent(expr, myExpression)) {
                    myList.add(expr);
                } else {
                    superVisitElement(expr);
                }
            }
        }

        private boolean isAccepted(XPathExpression expr) {
            final XmlAttribute attribute = PsiTreeUtil.getContextOfType(expr, XmlAttribute.class, true);
            return attribute != null && !XsltSupport.isPatternAttribute(attribute);
        }

        private boolean isEquivalent(XPathExpression expr, XPathExpression expression) {
            return XsltCodeInsightUtil.areExpressionsEquivalent(expr, expression);
        }

        public Set<XPathExpression> getMatches() {
            return myList;
        }
    }
}