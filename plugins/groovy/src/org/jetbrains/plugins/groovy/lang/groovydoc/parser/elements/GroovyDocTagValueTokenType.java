/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.groovydoc.parser.elements;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_TAG_VALUE_SHARP_TOKEN;
import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.IGroovyDocElementType;
import static org.jetbrains.plugins.groovy.lang.groovydoc.parser.elements.GroovyDocTagValueTokenType.TagValueTokenType.REFERENCE_ELEMENT;
import static org.jetbrains.plugins.groovy.lang.groovydoc.parser.elements.GroovyDocTagValueTokenType.TagValueTokenType.VALUE_TOKEN;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocInlinedTag;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParameter;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import java.util.Arrays;
import java.util.Set;

/**
 * @author ilyas
 */
public class GroovyDocTagValueTokenType extends GroovyDocChameleonElementType implements IGroovyDocElementType {

  private static final Set<String> TAGS_WITH_REFERENCES = new HashSet<String>();
  private static final Set<String> INLINED_TAGS_WITH_REFERENCES = new HashSet<String>();
  private static final Set<String> BUILT_IN_TYPES = new HashSet<String>();

  static {
    BUILT_IN_TYPES.addAll(Arrays.asList("double", "long", "float", "short", "any", "char", "int", "byte", "boolean"));
  }

  static {
    TAGS_WITH_REFERENCES.addAll(Arrays.asList("@see", "@throws", "@exception"));
    INLINED_TAGS_WITH_REFERENCES.addAll(Arrays.asList("@link", "@linkplain", "@value"));
  }

  public GroovyDocTagValueTokenType() {
    super("GDOC_TAG_VALUE_TOKEN");
  }

  public TagValueTokenType getValueType(@NotNull ASTNode node) {
    return isReferenceElement(node.getTreeParent(), node) ? REFERENCE_ELEMENT : VALUE_TOKEN;
  }

  public ASTNode parseContents(ASTNode chameleon) {
    ASTNode parent = chameleon.getTreeParent();
    if (isReferenceElement(parent, chameleon)) {
      return parseImpl(chameleon);
    }

    return getPlainVaueToken(chameleon);
  }

  private static boolean isReferenceElement(ASTNode parent, ASTNode child) {
    if (parent != null && child != null) {
      PsiElement parentPsi = parent.getPsi();
      if (parentPsi instanceof GrDocTag) {
        String name = ((GrDocTag) parentPsi).getName();
        if (TAGS_WITH_REFERENCES.contains(name) && !(parentPsi instanceof GrDocInlinedTag) ||
                INLINED_TAGS_WITH_REFERENCES.contains(name) && parentPsi instanceof GrDocInlinedTag) {
          return parent.findChildByType(mGDOC_TAG_VALUE_TOKEN) == child;
        }
      }
      if (parentPsi instanceof GrDocMethodParameter &&
              parent.findChildByType(mGDOC_TAG_VALUE_TOKEN) == child) return true;

      if (parentPsi instanceof GrDocMemberReference) {
        ASTNode prev = child.getTreePrev();
        if (prev != null && prev.getElementType() == mGDOC_TAG_VALUE_SHARP_TOKEN) return false;
        return parent.findChildByType(mGDOC_TAG_VALUE_TOKEN) == child;
      }
    }
    return false;
  }

  private static ASTNode getPlainVaueToken(ASTNode chameleon) {
    return new LeafPsiElement(GroovyDocTokenTypes.mGDOC_TAG_PLAIN_VALUE_TOKEN, chameleon.getText());
  }

  private ASTNode parseImpl(ASTNode chameleon) {
    final PeerFactory factory = PeerFactory.getInstance();
    final PsiElement parentElement = chameleon.getTreeParent().getPsi();
    final Project project = parentElement.getProject();
    final PsiBuilder builder = factory.createBuilder(chameleon, new GroovyLexer(), getLanguage(), chameleon.getText(), project);

    PsiBuilder.Marker rootMarker = builder.mark();
    if (BUILT_IN_TYPES.contains(chameleon.getText())) {
      builder.advanceLexer();
    } else {
      parseBody(builder);
    }
    rootMarker.done(this);
    return builder.getTreeBuilt().getFirstChildNode();
  }

  private static void parseBody(PsiBuilder builder) {
    ReferenceElement.parse(builder, false, false, false, false);
    while (!builder.eof()) {
      builder.advanceLexer();
    }
  }

  public static enum TagValueTokenType {
    REFERENCE_ELEMENT, VALUE_TOKEN
  }
}
