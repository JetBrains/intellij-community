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

import org.intellij.lang.xpath.XPathElementTypes;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.psi.*;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XPathStepImpl extends XPathElementImpl implements XPathStep {

    public XPathStepImpl(ASTNode node) {
        super(node);
    }

    @NotNull
    public XPathType getType() {
        return XPathType.NODESET;
    }

    public XPathAxisSpecifier getAxisSpecifier() {
        final ASTNode node = getNode().findChildByType(XPathElementTypes.AXIS_SPECIFIER);
        return (XPathAxisSpecifier)(node != null ? node.getPsi() : null);
    }

    public XPathNodeTest getNodeTest() {
        final ASTNode node = getNode().findChildByType(XPathElementTypes.NODE_TEST);
        return (XPathNodeTest)(node != null ? node.getPsi() : null);
    }

    @NotNull
    public XPathPredicate[] getPredicates() {
        final ASTNode[] nodes = getNode().getChildren(XPathElementTypes.PREDICATES);
        final XPathPredicate[] predicates = new XPathPredicate[nodes.length];
        for (int i = 0; i < predicates.length; i++) {
            predicates[i] = (XPathPredicate)nodes[i].getPsi();
        }
        return predicates;
    }

    @Nullable
    public XPathExpression getStepExpression() {
        final ASTNode[] nodes = getNode().getChildren(XPathElementTypes.STEPS);
        assert nodes.length <= 1;
        if (nodes.length > 0) {
            return (XPathExpression)nodes[0].getPsi();
        }
        return null;
    }

    public boolean isAbsolute() {
        return getStepExpression() == null && getNode().getChildren(XPathTokenTypes.PATH_OPS).length > 0;
    }
}
