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
import org.intellij.lang.xpath.psi.XPathString;
import org.intellij.lang.xpath.psi.XPathType;
import org.jetbrains.annotations.NotNull;

public class XPathStringImpl extends XPathElementImpl implements XPathString {
    public XPathStringImpl(ASTNode node) {
        super(node);
    }

    @NotNull
    public XPathType getType() {
        return XPathType.STRING;
    }

    public boolean isWellFormed() {
        final String text = getText();
        if (text.startsWith("'")) {
            if (!text.endsWith("'")) {
                return false;
            }
        } else if (text.startsWith("\"")) {
            if (!text.endsWith("\"")) {
                return false;
            }
        }
        return text.indexOf('\n') == -1 && text.indexOf("\r") == -1;
    }

    public String getValue() {
        return getText().substring(0, getTextLength() - 1);
    }
}