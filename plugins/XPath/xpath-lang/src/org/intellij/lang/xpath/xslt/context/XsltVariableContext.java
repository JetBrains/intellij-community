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
package org.intellij.lang.xpath.xslt.context;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.intellij.lang.xpath.context.VariableContext;
import org.intellij.lang.xpath.psi.XPathElement;
import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.quickfix.CreateParameterFix;
import org.intellij.lang.xpath.xslt.quickfix.CreateVariableFix;
import org.intellij.lang.xpath.xslt.util.ElementProcessor;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

import java.util.ArrayList;
import java.util.List;

public class XsltVariableContext implements VariableContext<XsltVariable> {
    public static final XsltVariableContext INSTANCE = new XsltVariableContext();

    @NotNull
    public XsltVariable[] getVariablesInScope(XPathElement element) {
        final XmlTag context = getContextTag(element);
        final VariantsProcessor processor = new VariantsProcessor(context);

        ResolveUtil.treeWalkUp(processor, context);
        return processor.getResult();
    }

    public XPathVariable resolve(XPathVariableReference reference) {
        final XmlTag context = getContextTag(reference);
        final ResolveProcessor processor = new ResolveProcessor(reference.getReferencedName(), context);
        
        return (XPathVariable)ResolveUtil.treeWalkUp(processor, context);
    }

    @Nullable
    protected XmlTag getContextTag(XPathElement element) {
        return PsiTreeUtil.getContextOfType(element, XmlTag.class, true);
    }

    @NotNull
    public IntentionAction[] getUnresolvedVariableFixes(XPathVariableReference reference) {
        return new IntentionAction[] {
                new CreateVariableFix(reference),
                new CreateParameterFix(reference)
        };
    }

    public boolean isReferenceTo(PsiElement element, XPathVariableReference reference) {
        if (element instanceof XsltParameter) {
            final XsltTemplate template = XsltCodeInsightUtil.getTemplate(element, false);
            if (template == null || template.getMatchExpression() == null) return false;

            final PsiReference[] references = element.getReferences();
            for (PsiReference r : references) {
                if (r.isReferenceTo(element)) return true;
            }
        }
        return false;
    }

    public boolean canResolve() {
        return true;
    }

    static abstract class VariableProcessor extends ElementProcessor<XmlTag> {
        public VariableProcessor(XmlTag context) {
            super(context);
        }

        protected boolean followImport() {
            return true;
        }

        protected final void processTemplate(XmlTag tag) {
            // not interested
        }

        protected abstract void processVarOrParamImpl(XmlTag tag);

        protected final void processVarOrParam(XmlTag tag) {
            if (tag != myRoot) {
                processVarOrParamImpl(tag);
            }
        }
    }

    static class VariantsProcessor extends VariableProcessor {
        private final List<XsltVariable> myNames = new ArrayList<XsltVariable>();

        public VariantsProcessor(XmlTag context) {
            super(context);
        }

        public XsltVariable[] getResult() {
            return myNames.toArray(new XsltVariable[myNames.size()]);
        }

        protected void processVarOrParamImpl(XmlTag tag) {
            if (XsltSupport.isVariableOrParam(tag)) {
                myNames.add(XsltElementFactory.getInstance().wrapElement(tag, XsltVariable.class));
            }
        }

        protected boolean shouldContinue() {
            return true;
        }
    }

    static class ResolveProcessor extends VariableProcessor implements ResolveUtil.ResolveProcessor {
        private final String myName;
        private PsiElement myResult = null;

        public ResolveProcessor(final String name, XmlTag context) {
            super(context);
            myName = name;
        }

        public PsiElement getResult() {
            return myResult;
        }

        protected boolean shouldContinue() {
            return myResult == null;
        }

        protected void processVarOrParamImpl(XmlTag tag) {
            if (XsltSupport.isVariableOrParam(tag)) {
                final String name = tag.getAttributeValue("name");
                if (myName.equals(name)) {
                    myResult = XsltElementFactory.getInstance().wrapElement(tag, XsltVariable.class);
                }
            }
        }
    }
}
