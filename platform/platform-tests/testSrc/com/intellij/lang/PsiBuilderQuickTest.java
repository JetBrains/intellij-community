// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.openapi.fileTypes.PlainTextParserDefinition;
import com.intellij.openapi.project.Project;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.ASTStructure;
import com.intellij.psi.tree.*;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.util.ThreeState;
import com.intellij.util.diff.DiffTree;
import com.intellij.util.diff.DiffTreeChangeBuilder;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import com.intellij.util.diff.ShallowNodeComparator;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class PsiBuilderQuickTest extends BareTestFixtureTestCase {
  private static final IFileElementType ROOT = new IFileElementType("ROOT", Language.ANY);

  private static final IElementType LETTER = new IElementType("LETTER", Language.ANY);
  private static final IElementType DIGIT = new IElementType("DIGIT", Language.ANY);
  private static final IElementType OTHER = new IElementType("OTHER", Language.ANY);
  private static final IElementType COLLAPSED = new IElementType("COLLAPSED", Language.ANY);
  private static final IElementType LEFT_BOUND = new IElementType("LEFT_BOUND", Language.ANY) {
    @Override
    public boolean isLeftBound() { return true; }
  };
  private static final IElementType COMMENT = new IElementType("COMMENT", Language.ANY);

  private static final TokenSet WHITESPACE_SET = TokenSet.WHITE_SPACE;
  private static final TokenSet COMMENT_SET = TokenSet.create(COMMENT);

  @Test
  public void testPlain() {
    doTest("a<<b",
           "Element(ROOT)\n" +
           "  PsiElement(LETTER)('a')\n" +
           "  PsiElement(OTHER)('<')\n" +
           "  PsiElement(OTHER)('<')\n" +
           "  PsiElement(LETTER)('b')\n", builder -> { while (builder.getTokenType() != null) builder.advanceLexer(); }
    );
  }

  @Test
  public void testComposites() {
    doTest("1(a(b)c)2(d)3",
           "Element(ROOT)\n" +
           "  PsiElement(DIGIT)('1')\n" +
           "  Element(OTHER)\n" +
           "    PsiElement(OTHER)('(')\n" +
           "    PsiElement(LETTER)('a')\n" +
           "    Element(OTHER)\n" +
           "      PsiElement(OTHER)('(')\n" +
           "      PsiElement(LETTER)('b')\n" +
           "      PsiElement(OTHER)(')')\n" +
           "    PsiElement(LETTER)('c')\n" +
           "    PsiElement(OTHER)(')')\n" +
           "  PsiElement(DIGIT)('2')\n" +
           "  Element(OTHER)\n" +
           "    PsiElement(OTHER)('(')\n" +
           "    Element(OTHER)\n" +
           "      <empty list>\n" +
           "    PsiElement(LETTER)('d')\n" +
           "    PsiElement(OTHER)(')')\n" +
           "  PsiElement(DIGIT)('3')\n", builder -> {
             PsiBuilderUtil.advance(builder, 1);
             PsiBuilder.Marker marker1 = builder.mark();
             PsiBuilderUtil.advance(builder, 2);
             PsiBuilder.Marker marker2 = builder.mark();
             PsiBuilderUtil.advance(builder, 3);
             marker2.done(OTHER);
             PsiBuilderUtil.advance(builder, 2);
             marker1.done(OTHER);
             PsiBuilderUtil.advance(builder, 1);
             PsiBuilder.Marker marker3 = builder.mark();
             PsiBuilderUtil.advance(builder, 1);
             builder.mark().done(OTHER);
             PsiBuilderUtil.advance(builder, 2);
             marker3.done(OTHER);
             PsiBuilderUtil.advance(builder, 1);
           }
    );
  }

  @Test
  public void testCollapse() {
    doTest("a<<>>b",
           "Element(ROOT)\n" +
           "  PsiElement(LETTER)('a')\n" +
           "  PsiElement(COLLAPSED)('<<')\n" +
           "  PsiElement(COLLAPSED)('>>')\n" +
           "  PsiElement(LETTER)('b')\n", builder -> {
             PsiBuilderUtil.advance(builder, 1);
             PsiBuilder.Marker marker1 = builder.mark();
             PsiBuilderUtil.advance(builder, 2);
             marker1.collapse(COLLAPSED);
             PsiBuilder.Marker marker2 = builder.mark();
             PsiBuilderUtil.advance(builder, 2);
             marker2.collapse(COLLAPSED);
             PsiBuilderUtil.advance(builder, 1);
           }
    );
  }

  @Test
  public void testDoneAndError() {
    doTest("a2b",
           "Element(ROOT)\n" +
           "  Element(LETTER)\n" +
           "    PsiElement(LETTER)('a')\n" +
           "  PsiErrorElement:no digits allowed\n" +
           "    PsiElement(DIGIT)('2')\n" +
           "  Element(LETTER)\n" +
           "    PsiElement(LETTER)('b')\n", builder -> {
             IElementType tokenType;
             while ((tokenType = builder.getTokenType()) != null) {
               PsiBuilder.Marker marker = builder.mark();
               builder.advanceLexer();
               if (tokenType == DIGIT) marker.error("no digits allowed"); else marker.done(tokenType);
             }
           }
    );
  }

  @Test
  public void testPrecedeAndDoneBefore() {
    doTest("ab",
           "Element(ROOT)\n" +
           "  Element(COLLAPSED)\n" +
           "    PsiElement(LETTER)('a')\n" +
           "    Element(COLLAPSED)\n" +
           "      <empty list>\n" +
           "    PsiErrorElement:with error\n" +
           "      <empty list>\n" +
           "  Element(OTHER)\n" +
           "    PsiElement(LETTER)('b')\n", builder -> {
             PsiBuilder.Marker marker1 = builder.mark();
             builder.advanceLexer();
             PsiBuilder.Marker marker2 = builder.mark();
             builder.advanceLexer();
             marker2.done(OTHER);
             marker2.precede().doneBefore(COLLAPSED, marker2);
             marker1.doneBefore(COLLAPSED, marker2, "with error");
           }
    );
  }

  @Test
  public void testErrorBefore() {
    doTest("a1",
           "Element(ROOT)\n" +
           "  Element(LETTER)\n" +
           "    PsiElement(LETTER)('a')\n" +
           "  PsiErrorElement:something lost\n" +
           "    <empty list>\n" +
           "  Element(DIGIT)\n" +
           "    PsiElement(DIGIT)('1')\n", builder -> {
             PsiBuilder.Marker letter = builder.mark();
             builder.advanceLexer();
             letter.done(LETTER);
             PsiBuilder.Marker digit = builder.mark();
             builder.advanceLexer();
             digit.done(DIGIT);
             digit.precede().errorBefore("something lost", digit);
           }
    );
  }

  @Test
  public void testValidityChecksOnDone() {
    doFailTest("a",
               "Another not done marker added after this one. Must be done before this.", builder -> {
                 PsiBuilder.Marker first = builder.mark();
                 builder.advanceLexer();
                 builder.mark();
                 first.done(LETTER);
               }
    );
  }

  @Test
  public void testValidityChecksOnDoneBefore1() {
    doFailTest("a",
               "Another not done marker added after this one. Must be done before this.", builder -> {
                 PsiBuilder.Marker first = builder.mark();
                 builder.advanceLexer();
                 PsiBuilder.Marker second = builder.mark();
                 second.precede();
                 first.doneBefore(LETTER, second);
               }
    );
  }

  @Test
  public void testValidityChecksOnDoneBefore2() {
    doFailTest("a",
               "'Before' marker precedes this one.", builder -> {
                 PsiBuilder.Marker first = builder.mark();
                 builder.advanceLexer();
                 PsiBuilder.Marker second = builder.mark();
                 second.doneBefore(LETTER, first);
               }
    );
  }

  @Test
  public void testValidityChecksOnTreeBuild1() {
    doFailTest("aa",
               "Parser produced no markers. Text:\naa", builder -> { while(!builder.eof()) builder.advanceLexer(); }
    );
  }

  @Test
  public void testValidityChecksOnTreeBuild2() {
    doFailTest("aa",
               "Tokens [LETTER] were not inserted into the tree. Text:\naa", builder -> {
                 PsiBuilder.Marker marker = builder.mark();
                 builder.advanceLexer();
                 marker.done(LETTER);
               }
    );
  }

  @Test
  public void testValidityChecksOnTreeBuild3() {
    doFailTest("a ",
               "Tokens [WHITE_SPACE] are outside of root element \"LETTER\". Text:\na ", builder -> {
                 PsiBuilder.Marker marker = builder.mark();
                 builder.advanceLexer();
                 marker.done(LETTER);
                 while(!builder.eof()) builder.advanceLexer();
               }
    );
  }

  @Test
  public void testWhitespaceTrimming() {
    doTest(" a b ",
           "Element(ROOT)\n" +
           "  PsiWhiteSpace(' ')\n" +
           "  Element(OTHER)\n" +
           "    PsiElement(LETTER)('a')\n" +
           "  PsiWhiteSpace(' ')\n" +
           "  Element(OTHER)\n" +
           "    PsiElement(LETTER)('b')\n" +
           "  PsiWhiteSpace(' ')\n", builder -> {
             PsiBuilder.Marker marker = builder.mark();
             builder.advanceLexer();
             marker.done(OTHER);
             marker = builder.mark();
             builder.advanceLexer();
             marker.done(OTHER);
             builder.advanceLexer();
           }
    );
  }

  @Test
  public void testWhitespaceBalancingByErrors() {
    doTest("a b c",
           "Element(ROOT)\n" +
           "  Element(OTHER)\n" +
           "    PsiElement(LETTER)('a')\n" +
           "    PsiErrorElement:error 1\n" +
           "      <empty list>\n" +
           "  PsiWhiteSpace(' ')\n" +
           "  Element(OTHER)\n" +
           "    PsiElement(LETTER)('b')\n" +
           "    PsiErrorElement:error 2\n" +
           "      <empty list>\n" +
           "  PsiWhiteSpace(' ')\n" +
           "  PsiErrorElement:error 3\n" +
           "    PsiElement(LETTER)('c')\n", builder -> {
             PsiBuilder.Marker marker = builder.mark();
             builder.advanceLexer();
             builder.error("error 1");
             marker.done(OTHER);
             marker = builder.mark();
             builder.advanceLexer();
             builder.mark().error("error 2");
             marker.done(OTHER);
             marker = builder.mark();
             builder.advanceLexer();
             marker.error("error 3");
           }
    );
  }

  @Test
  public void testWhitespaceBalancingByEmptyComposites() {
    doTest("a b c",
           "Element(ROOT)\n" +
           "  Element(OTHER)\n" +
           "    PsiElement(LETTER)('a')\n" +
           "    PsiWhiteSpace(' ')\n" +
           "    Element(OTHER)\n" +
           "      <empty list>\n" +
           "  Element(OTHER)\n" +
           "    PsiElement(LETTER)('b')\n" +
           "    Element(LEFT_BOUND)\n" +
           "      <empty list>\n" +
           "  PsiWhiteSpace(' ')\n" +
           "  PsiElement(LETTER)('c')\n", builder -> {
             PsiBuilder.Marker marker = builder.mark();
             builder.advanceLexer();
             builder.mark().done(OTHER);
             marker.done(OTHER);
             marker = builder.mark();
             builder.advanceLexer();
             builder.mark().done(LEFT_BOUND);
             marker.done(OTHER);
             builder.advanceLexer();
           }
    );
  }

  @Test
  public void testCustomEdgeProcessors() {
    WhitespacesAndCommentsBinder leftEdgeProcessor = new WhitespacesAndCommentsBinder() {
      @Override
      public int getEdgePosition(List<? extends IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
        int pos = tokens.size() - 1;
        while (tokens.get(pos) != COMMENT && pos > 0) pos--;
        return pos;
      }
    };
    WhitespacesAndCommentsBinder rightEdgeProcessor = new WhitespacesAndCommentsBinder() {
      @Override
      public int getEdgePosition(List<? extends IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
        int pos = 0;
        while (tokens.get(pos) != COMMENT && pos < tokens.size()-1) pos++;
        return pos + 1;
      }
    };

    doTest("{ # i # }",
           "Element(ROOT)\n" +
           "  PsiElement(OTHER)('{')\n" +
           "  PsiWhiteSpace(' ')\n" +
           "  Element(OTHER)\n" +
           "    PsiElement(COMMENT)('#')\n" +
           "    PsiWhiteSpace(' ')\n" +
           "    PsiElement(LETTER)('i')\n" +
           "    PsiWhiteSpace(' ')\n" +
           "    PsiElement(COMMENT)('#')\n" +
           "  PsiWhiteSpace(' ')\n" +
           "  PsiElement(OTHER)('}')\n", builder -> {
             while (builder.getTokenType() != LETTER) builder.advanceLexer();
             PsiBuilder.Marker marker = builder.mark();
             builder.advanceLexer();
             marker.done(OTHER);
             marker.setCustomEdgeTokenBinders(leftEdgeProcessor, rightEdgeProcessor);
             while (builder.getTokenType() != null) builder.advanceLexer();
           }
    );
  }

  @Test
  public void testLightChameleon() {
    IElementType CHAMELEON_2 = new MyChameleon2Type();
    IElementType CHAMELEON_1 = new MyChameleon1Type(CHAMELEON_2);

    doTest("ab{12[.?]}cd{x}",
           "Element(ROOT)\n" +
           "  PsiElement(LETTER)('a')\n" +
           "  PsiElement(LETTER)('b')\n" +
           "  Element(CHAMELEON_1)\n" +
           "    PsiElement(OTHER)('{')\n" +
           "    PsiElement(DIGIT)('1')\n" +
           "    PsiElement(DIGIT)('2')\n" +
           "    Element(OTHER)\n" +
           "      Element(CHAMELEON_2)\n" +
           "        PsiElement(OTHER)('[')\n" +
           "        PsiElement(OTHER)('.')\n" +
           "        PsiErrorElement:test error 2\n" +
           "          PsiElement(OTHER)('?')\n" +
           "        PsiElement(OTHER)(']')\n" +
           "    PsiErrorElement:test error 1\n" +
           "      <empty list>\n" +
           "    PsiElement(OTHER)('}')\n" +
           "  PsiElement(LETTER)('c')\n" +
           "  PsiElement(LETTER)('d')\n" +
           "  Element(CHAMELEON_1)\n" +
           "    PsiElement(OTHER)('{')\n" +
           "    PsiElement(LETTER)('x')\n" +
           "    PsiElement(OTHER)('}')\n", builder -> {
             PsiBuilderUtil.advance(builder, 2);
             PsiBuilder.Marker chameleon = builder.mark();
             PsiBuilderUtil.advance(builder, 8);
             chameleon.collapse(CHAMELEON_1);
             PsiBuilderUtil.advance(builder, 2);
             chameleon = builder.mark();
             PsiBuilderUtil.advance(builder, 3);
             chameleon.collapse(CHAMELEON_1);
           }
    );
  }

  @Test
  public void testLightChameleonIsParsedOnce() {
    AtomicInteger parserInvocations = new AtomicInteger();

    PsiBuilder builder = createBuilder("{x}");
    PsiBuilder.Marker rootMarker = builder.mark();
    PsiBuilder.Marker chameleon = builder.mark();
    PsiBuilderUtil.advance(builder, 3);
    chameleon.collapse(new MyChameleon2Type() {
      @Override
      public void parse(PsiBuilder builder1) {
        parserInvocations.incrementAndGet();
        super.parse(builder1);
      }
    });
    rootMarker.done(ROOT);

    String treeString = "Element(ROOT)\n" +
                        "  Element(CHAMELEON_2)\n" +
                        "    PsiElement(OTHER)('{')\n" +
                        "    PsiElement(LETTER)('x')\n" +
                        "    PsiElement(OTHER)('}')\n";
    FlyweightCapableTreeStructure<LighterASTNode> tree = builder.getLightTree();
    assertEquals(treeString, DebugUtil.lightTreeToString(tree, false));
    assertEquals(1, parserInvocations.get());

    assertEquals(treeString, DebugUtil.lightTreeToString(tree, false));
    assertEquals(1, parserInvocations.get());

    // new tree
    assertEquals(treeString, DebugUtil.lightTreeToString(builder.getLightTree(), false));
    assertEquals(1, parserInvocations.get());
  }

  @Test
  public void testEndMarkersOverlapping() {
    doTest("a ",
           "Element(ROOT)\n" +
           "  Element(OTHER)\n" +
           "    Element(OTHER)\n" +
           "      PsiElement(LETTER)('a')\n" +
           "      PsiWhiteSpace(' ')\n", builder -> {
             PsiBuilder.Marker e1 = builder.mark();
             PsiBuilder.Marker e2 = builder.mark();
             builder.advanceLexer();
             e2.done(OTHER);
             e2.setCustomEdgeTokenBinders(null, WhitespacesBinders.GREEDY_RIGHT_BINDER);
             e1.done(OTHER);
             e1.setCustomEdgeTokenBinders(null, WhitespacesBinders.DEFAULT_RIGHT_BINDER);
             assertTrue(builder.eof());
           }
    );
  }

  @Test
  public void testEmptyCollapsedNode() {
    doTest("a<<b",
           "Element(ROOT)\n" +
           "  PsiElement(LETTER)('a')\n" +
           "  PsiElement(COLLAPSED)('')\n" +
           "  PsiElement(OTHER)('<')\n" +
           "  PsiElement(OTHER)('<')\n" +
           "  PsiElement(LETTER)('b')\n", builder -> {
             builder.advanceLexer();
             builder.mark().collapse(COLLAPSED);
             while (builder.getTokenType() != null) {
               builder.advanceLexer();
             }
           }
    );
  }

  private static void doTest(String text, String expected, Consumer<? super PsiBuilder> parser) {
    PsiBuilder builder = createBuilder(text);
    PsiBuilder.Marker rootMarker = builder.mark();
    parser.accept(builder);
    rootMarker.done(ROOT);

    // check light tree composition
    FlyweightCapableTreeStructure<LighterASTNode> lightTree = builder.getLightTree();
    assertEquals(expected, DebugUtil.lightTreeToString(lightTree, false));
    // verify that light tree can be taken multiple times
    FlyweightCapableTreeStructure<LighterASTNode> lightTree2 = builder.getLightTree();
    assertEquals(expected, DebugUtil.lightTreeToString(lightTree2, false));

    // check heavy tree composition
    ASTNode root = builder.getTreeBuilt();
    assertEquals(expected, DebugUtil.nodeTreeToString(root, false));

    // check heavy vs. light tree merging
    PsiBuilder builder2 = createBuilder(text);
    PsiBuilder.Marker rootMarker2 = builder2.mark();
    parser.accept(builder2);
    rootMarker2.done(ROOT);
    DiffTree.diff(
      new ASTStructure(root), builder2.getLightTree(),
      new ShallowNodeComparator<>() {
        @NotNull
        @Override
        public ThreeState deepEqual(@NotNull ASTNode oldNode, @NotNull LighterASTNode newNode) {
          return ThreeState.UNSURE;
        }

        @Override
        public boolean typesEqual(@NotNull ASTNode oldNode, @NotNull LighterASTNode newNode) {
          return true;
        }

        @Override
        public boolean hashCodesEqual(@NotNull ASTNode oldNode, @NotNull LighterASTNode newNode) {
          return true;
        }
      },
      new DiffTreeChangeBuilder<>() {
        @Override
        public void nodeReplaced(@NotNull ASTNode oldChild, @NotNull LighterASTNode newChild) {
          fail("replaced(" + oldChild + "," + newChild.getTokenType() + ")");
        }

        @Override
        public void nodeDeleted(@NotNull ASTNode oldParent, @NotNull ASTNode oldNode) {
          fail("deleted(" + oldParent + "," + oldNode + ")");
        }

        @Override
        public void nodeInserted(@NotNull ASTNode oldParent, @NotNull LighterASTNode newNode, int pos) {
          fail("inserted(" + oldParent + "," + newNode.getTokenType() + ")");
        }
      },
      root.getText());
  }

  private static void doFailTest(String text, String expected, Consumer<? super PsiBuilder> parser) {
    PlatformTestUtil.withStdErrSuppressed(() -> {
      try {
        PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(new PlainTextParserDefinition(), new MyTestLexer(), text);
        builder.setDebugMode(true);
        parser.accept(builder);
        builder.getLightTree();
        fail("should fail");
      }
      catch (AssertionError e) {
        assertEquals(expected, e.getMessage());
      }
    });
  }

  private static PsiBuilderImpl createBuilder(CharSequence text) {
    ParserDefinition parserDefinition = new PlainTextParserDefinition() {
      @NotNull
      @Override
      public Lexer createLexer(Project project) {
        return new MyTestLexer();
      }

      @NotNull
      @Override
      public TokenSet getWhitespaceTokens() {
        return WHITESPACE_SET;
      }

      @NotNull
      @Override
      public TokenSet getCommentTokens() {
        return COMMENT_SET;
      }
    };
    return new PsiBuilderImpl(null, null, parserDefinition, parserDefinition.createLexer(null), null, text, null, null);
  }

  private static class MyTestLexer extends LexerBase {
    private CharSequence myBuffer = "";
    private int myIndex = 0;
    private int myBufferEnd = 1;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
      myBuffer = buffer.subSequence(startOffset, endOffset);
      myIndex = 0;
      myBufferEnd = myBuffer.length();
    }

    @Override
    public int getState() {
      return 0;
    }

    @Override
    public IElementType getTokenType() {
      if (myIndex >= myBufferEnd) return null;
      else if (Character.isLetter(myBuffer.charAt(myIndex))) return LETTER;
      else if (Character.isDigit(myBuffer.charAt(myIndex))) return DIGIT;
      else if (Character.isWhitespace(myBuffer.charAt(myIndex))) return TokenType.WHITE_SPACE;
      else if (myBuffer.charAt(myIndex) == '#') return COMMENT;
      else return OTHER;
    }

    @Override
    public int getTokenStart() {
      return myIndex;
    }

    @Override
    public int getTokenEnd() {
      return myIndex + 1;
    }

    @Override
    public void advance() {
      if (myIndex < myBufferEnd) myIndex++;
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
      return myBuffer;
    }

    @Override
    public int getBufferEnd() {
      return myBufferEnd;
    }
  }

  private abstract static class MyLazyElementType extends ILazyParseableElementType implements ILightLazyParseableElementType {
    protected MyLazyElementType(String debugName) {
      super(debugName, Language.ANY);
    }
  }

  private static class MyChameleon1Type extends MyLazyElementType {
    private final IElementType myCHAMELEON_2;

    MyChameleon1Type(IElementType CHAMELEON_2) {
      super("CHAMELEON_1");
      myCHAMELEON_2 = CHAMELEON_2;
    }

    @Override
    public FlyweightCapableTreeStructure<LighterASTNode> parseContents(LighterLazyParseableNode chameleon) {
      PsiBuilder builder = createBuilder(chameleon.getText());
      parse(builder);
      return builder.getLightTree();
    }

    @Override
    public ASTNode parseContents(@NotNull ASTNode chameleon) {
      PsiBuilder builder = createBuilder(chameleon.getText());
      parse(builder);
      return builder.getTreeBuilt().getFirstChildNode();
    }

    public void parse(PsiBuilder builder) {
      PsiBuilder.Marker root = builder.mark();
      PsiBuilder.Marker nested = null;
      while (!builder.eof()) {
        String token = builder.getTokenText();
        if ("[".equals(token) && nested == null) {
          nested = builder.mark();
        }
        builder.advanceLexer();
        if ("]".equals(token) && nested != null) {
          nested.collapse(myCHAMELEON_2);
          nested.precede().done(OTHER);
          nested = null;
          builder.error("test error 1");
        }
      }
      if (nested != null) nested.drop();
      root.done(this);
    }
  }

  private static class MyChameleon2Type extends MyLazyElementType {
    MyChameleon2Type() {
      super("CHAMELEON_2");
    }

    @Override
    public FlyweightCapableTreeStructure<LighterASTNode> parseContents(LighterLazyParseableNode chameleon) {
      PsiBuilder builder = createBuilder(chameleon.getText());
      parse(builder);
      return builder.getLightTree();
    }

    @Override
    public ASTNode parseContents(@NotNull ASTNode chameleon) {
      PsiBuilder builder = createBuilder(chameleon.getText());
      parse(builder);
      return builder.getTreeBuilt().getFirstChildNode();
    }

    public void parse(PsiBuilder builder) {
      PsiBuilder.Marker root = builder.mark();
      PsiBuilder.Marker error = null;
      while (!builder.eof()) {
        String token = builder.getTokenText();
        if ("?".equals(token)) error = builder.mark();
        builder.advanceLexer();
        if (error != null) {
          error.error("test error 2");
          error = null;
        }
      }
      root.done(this);
    }
  }
}