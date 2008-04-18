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
package org.intellij.lang.xpath.xslt.refactoring.introduceParameter;

import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nullable;

class XPathUsageInfo extends UsageInfo {
    private final XPathExpression myExpression;

    @SuppressWarnings({ "ConstantConditions" })
    private XPathUsageInfo(XPathExpression expression, int startOffset, int endOffset) {
        super(expression.getContainingFile().getContext(), startOffset, endOffset, false);
        myExpression = expression;
    }

    @Nullable
    public XmlAttribute getAttribute() {
        return PsiTreeUtil.getContextOfType(myExpression, XmlAttribute.class, true);
    }

    public XPathExpression getExpression() {
        return myExpression;
    }

    public static XPathUsageInfo create(XPathExpression expression) {
        final TextRange range = XsltCodeInsightUtil.getRangeInsideHost(expression);
        return new XPathUsageInfo(expression, range.getStartOffset(), range.getEndOffset());
    }
}
