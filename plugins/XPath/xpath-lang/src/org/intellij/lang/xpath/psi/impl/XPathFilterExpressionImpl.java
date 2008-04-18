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
package org.intellij.lang.xpath.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import org.intellij.lang.xpath.XPathElementTypes;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.XPathFilterExpression;
import org.intellij.lang.xpath.psi.XPathPredicate;
import org.intellij.lang.xpath.psi.XPathType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XPathFilterExpressionImpl extends XPathElementImpl implements XPathFilterExpression {
    public XPathFilterExpressionImpl(ASTNode node) {
        super(node);
    }

    @NotNull
    public XPathType getType() {
        final XPathExpression expression = getExpression();
        return expression != null ? expression.getType() : XPathType.UNKNOWN;
    }

    @Nullable
    public XPathExpression getExpression() {
        final ASTNode[] nodes = getNode().getChildren(XPathElementTypes.EXPRESSIONS);
        return (XPathExpression)(nodes.length > 0 ? nodes[0].getPsi() : null);
    }

    @NotNull
    public XPathPredicate getPredicate() {
        final ASTNode[] nodes = getNode().getChildren(TokenSet.create(XPathElementTypes.PREDICATE));
        assert nodes.length == 1;
        return (XPathPredicate)nodes[0].getPsi();
    }
}
