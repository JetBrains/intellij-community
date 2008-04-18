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

/*
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 06.05.2006
 * Time: 12:56:57
 */
package org.intellij.lang.xpath.xslt.impl.references;

import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.quickfix.CreateTemplateFix;
import org.intellij.lang.xpath.xslt.util.NamedTemplateMatcher;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;

import java.util.List;

class TemplateReference extends AttributeReference implements EmptyResolveMessageProvider, QuickFixProvider<TemplateReference> {
    private final String myName;

    public TemplateReference(XmlAttribute attribute) {
        super(attribute, createMatcher(attribute), false);
        myName = attribute.getValue();
    }

    private static ResolveUtil.Matcher createMatcher(XmlAttribute attribute) {
        return new NamedTemplateMatcher(PsiTreeUtil.getParentOfType(attribute, XmlDocument.class), attribute.getValue());
    }

    public void registerQuickfix(HighlightInfo highlightInfo, TemplateReference psiReference) {
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateTemplateFix(myAttribute.getParent(), myName), (List<IntentionAction>)null, null);
    }

    public String getUnresolvedMessagePattern() {
        return "Cannot resolve template ''{0}''";
    }
}
