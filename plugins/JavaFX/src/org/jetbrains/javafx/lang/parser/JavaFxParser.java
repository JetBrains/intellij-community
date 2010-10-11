package org.jetbrains.javafx.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.lang.JavaFxElementType;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;

/**
 * Psi parser for JavaFx
 *
 * @author Alexey.Ivanov
 */
public class JavaFxParser implements PsiParser {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.javafx.lang.parser.JavaFxParser");
  protected PsiBuilder myBuilder;
  private IElementType myPreviousTokenType;

  protected boolean atToken(final IElementType tokenType) {
    return myBuilder.getTokenType() == tokenType;
  }

  private void nextToken() {
    myPreviousTokenType = myBuilder.getTokenType();
    myBuilder.advanceLexer();
  }

  protected void checkMatches(final IElementType token, final String message) {
    if (myBuilder.getTokenType() == token) {
      nextToken();
    }
    else {
      myBuilder.error(message);
    }
  }

  protected void checkMatches(final TokenSet tokenSet, final String message) {
    if (tokenSet.contains(myBuilder.getTokenType())) {
      nextToken();
    }
    else {
      myBuilder.error(message);
    }
  }

  @NotNull
  public ASTNode parse(final IElementType root, @NotNull final PsiBuilder builder) {
    myBuilder = builder;
    myBuilder.setDebugMode(true);
    final PsiBuilder.Marker rootMarker = builder.mark();
    final long start = System.nanoTime();
    parseRoot();
    LOG.info(String.format("Parsing time: %d", (System.nanoTime() - start)));
    rootMarker.done(root);
    return builder.getTreeBuilt();
  }

  private void parseRoot() {
    if (atToken(JavaFxTokenTypes.PACKAGE_KEYWORD)) {
      parsePackageDefinition();
    }
    while (!myBuilder.eof()) {
      parseScriptItem();
      checkForSemicolon();
    }
  }

  private void parsePackageDefinition() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.PACKAGE_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    parseQualifiedName(JavaFxElementTypes.REFERENCE_ELEMENT);
    checkForSemicolon();
    marker.done(JavaFxElementTypes.PACKAGE_DEFINITION);
  }

  private void parseScriptItem() {
    final IElementType firstToken = myBuilder.getTokenType();
    if (firstToken == JavaFxTokenTypes.IMPORT_KEYWORD) {
      parseImportList();
    }
    else if (firstToken == JavaFxTokenTypes.CLASS_KEYWORD) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      parseModifiers();
      parseClassDefinition(marker);
    }
    else if (firstToken == JavaFxTokenTypes.FUNCTION_KEYWORD) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      parseModifiers();
      parseFunctionDefinition(marker);
    }
    else if (JavaFxElementTypes.MODIFIERS.contains(firstToken)) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      parseModifiers();
      if (atToken(JavaFxTokenTypes.CLASS_KEYWORD)) {
        parseClassDefinition(marker);
      }
      else if (atToken(JavaFxTokenTypes.FUNCTION_KEYWORD)) {
        parseFunctionDefinition(marker);
      }
      else {
        marker.rollbackTo();
        parseExpression();
      }
    }
    else if (firstToken == JavaFxTokenTypes.SEMICOLON) {
      nextToken();
    }
    else if (firstToken != null) {
      parseExpression();
    }
  }

  private void parseImportList() {
    final PsiBuilder.Marker importList = myBuilder.mark();
    while (atToken(JavaFxTokenTypes.IMPORT_KEYWORD)) {
      parseImportStatement();
      checkForSemicolon();
    }
    importList.done(JavaFxElementTypes.IMPORT_LIST);
  }

  private void parseImportStatement() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.IMPORT_KEYWORD));
    final PsiBuilder.Marker importStatement = myBuilder.mark();
    nextToken();
    PsiBuilder.Marker reference = myBuilder.mark();
    checkMatches(JavaFxTokenTypes.NAME_ALL, JavaFxBundle.message("name.expected"));
    reference.done(JavaFxElementTypes.REFERENCE_ELEMENT);
    while (atToken(JavaFxElementTypes.DOT)) {
      nextToken();
      if (atToken(JavaFxElementTypes.MULT)) {
        nextToken();
        break;
      }
      reference = createReferenceFragment(JavaFxElementTypes.REFERENCE_ELEMENT, reference);
    }
    importStatement.done(JavaFxElementTypes.IMPORT_STATEMENT);
  }

  private void parseExpression() {
    final IElementType firstToken = myBuilder.getTokenType();
    if (firstToken == JavaFxTokenTypes.INSERT_KEYWORD) {
      parseInsertExpression();
    }
    else if (firstToken == JavaFxTokenTypes.DELETE_KEYWORD) {
      parseDeleteExpression();
    }
    else if (firstToken == JavaFxTokenTypes.WHILE_KEYWORD) {
      parseWhileExpression();
    }
    else if (firstToken == JavaFxTokenTypes.BREAK_KEYWORD) {
      parseBreakExpression();
    }
    else if (firstToken == JavaFxTokenTypes.CONTINUE_KEYWORD) {
      parseContinueExpression();
    }
    else if (firstToken == JavaFxTokenTypes.THROW_KEYWORD) {
      parseThrowExpression();
    }
    else if (firstToken == JavaFxTokenTypes.RETURN_KEYWORD) {
      parseReturnExpression();
    }
    else if (firstToken == JavaFxTokenTypes.TRY_KEYWORD) {
      parseTryExpression();
    }
    else if (firstToken == JavaFxTokenTypes.INVALIDATE_KEYWORD) {
      parseInvalidateExpression();
    }
    else {
      if (!parseValueExpressionOptional()) {
        myBuilder.error(JavaFxBundle.message("expression.expected"));
        nextToken();
      }
    }
  }

  private void checkForSemicolon() {
    final IElementType currentToken = myBuilder.getTokenType();
    if (currentToken == JavaFxTokenTypes.SEMICOLON) {
      nextToken();
      return;
    }
    if (currentToken == null ||
        currentToken == JavaFxTokenTypes.RBRACE ||
        currentToken == JavaFxTokenTypes.ELSE_KEYWORD ||
        currentToken == JavaFxTokenTypes.RBRACE_STRING_LITERAL ||
        currentToken == JavaFxTokenTypes.LBRACE_RBRACE_STRING_LITERAL) {
      return;
    }
    if (myPreviousTokenType == null ||
        myPreviousTokenType == JavaFxTokenTypes.RBRACE ||
        myPreviousTokenType == JavaFxTokenTypes.SEMICOLON) {
      return;
    }
    myBuilder.error(JavaFxBundle.message("semicolon.expected"));
  }

  private void parseClassDefinition(final PsiBuilder.Marker marker) {
    LOG.assertTrue(atToken(JavaFxTokenTypes.CLASS_KEYWORD));
    nextToken();
    checkMatches(JavaFxTokenTypes.NAME, JavaFxBundle.message("name.expected"));
    final PsiBuilder.Marker extendsListMarker = myBuilder.mark();
    if (atToken(JavaFxTokenTypes.EXTENDS_KEYWORD)) {
      nextToken();
      parseQualifiedName(JavaFxElementTypes.REFERENCE_ELEMENT);
      while (atToken(JavaFxTokenTypes.COMMA)) {
        nextToken();
        parseQualifiedName(JavaFxElementTypes.REFERENCE_ELEMENT);
      }
    }
    extendsListMarker.done(JavaFxElementTypes.REFERENCE_LIST);
    checkMatches(JavaFxTokenTypes.LBRACE, JavaFxBundle.message("lbrace.expected"));
    while (!atToken(JavaFxTokenTypes.RBRACE)) {
      if (myBuilder.eof()) {
        myBuilder.error(JavaFxBundle.message("rbrace.expected"));
        marker.done(JavaFxElementTypes.CLASS_DEFINITION);
        return;
      }
      parseClassMember();
      checkForSemicolon();
    }
    nextToken();
    marker.done(JavaFxElementTypes.CLASS_DEFINITION);
  }

  private void parseClassMember() {
    final IElementType firstToken = myBuilder.getTokenType();
    if (firstToken == JavaFxTokenTypes.INIT_KEYWORD) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      nextToken();
      parseBlockExpression();
      marker.done(JavaFxElementTypes.INIT_BLOCK);
    }
    else if (firstToken == JavaFxTokenTypes.POSTINIT_KEYWORD) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      nextToken();
      parseBlockExpression();
      marker.done(JavaFxElementTypes.POSTINIT_BLOCK);
    }
    else if (firstToken == JavaFxTokenTypes.FUNCTION_KEYWORD) {
      final PsiBuilder.Marker functionMarker = myBuilder.mark();
      parseModifiers();
      parseFunctionDefinition(functionMarker);
    }
    else if (JavaFxTokenTypes.VARIABLE_LABEL.contains(firstToken)) {
      final PsiBuilder.Marker variableMarker = myBuilder.mark();
      parseModifiers();
      parseVariableDeclaration(variableMarker);
    }
    else if (JavaFxTokenTypes.MODIFIERS.contains(firstToken)) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      parseModifiers();
      if (atToken(JavaFxTokenTypes.FUNCTION_KEYWORD)) {
        parseFunctionDefinition(marker);
      }
      else if (JavaFxTokenTypes.VARIABLE_LABEL.contains(myBuilder.getTokenType())) {
        parseVariableDeclaration(marker);
      }
      else {
        myBuilder.error(JavaFxBundle.message("unexpected.token"));
        marker.drop();
      }
    }
    else if (firstToken == JavaFxTokenTypes.SEMICOLON) {
      do {
        nextToken();
      }
      while (atToken(JavaFxTokenTypes.SEMICOLON));
    }
    else {
      myBuilder.error(JavaFxBundle.message("unexpected.token"));
      nextToken();
    }
  }

  private void parseModifiers() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    while (JavaFxTokenTypes.MODIFIERS.contains(myBuilder.getTokenType())) {
      nextToken();
    }
    marker.done(JavaFxElementTypes.MODIFIER_LIST);
  }

  private void parseFunctionDefinition(final PsiBuilder.Marker marker) {
    LOG.assertTrue(atToken(JavaFxTokenTypes.FUNCTION_KEYWORD));
    nextToken();
    checkMatches(JavaFxTokenTypes.NAME, JavaFxBundle.message("name.expected"));
    parseFunctionSignature(true);
    if (atToken(JavaFxTokenTypes.LBRACE)) {
      parseBlockExpression();
    }
    marker.done(JavaFxElementTypes.FUNCTION_DEFINITION);
  }

  private void parseInsertExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.INSERT_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    parseValueExpression();
    if (atToken(JavaFxTokenTypes.INTO_KEYWORD)) {
      nextToken();
      parseValueExpression();
    }
    else if (atToken(JavaFxTokenTypes.AFTER_KEYWORD) || atToken(JavaFxTokenTypes.BEFORE_KEYWORD)) {
      nextToken();
      parseIndexedSequenceForInsert();
    }
    else {
      myBuilder.error(JavaFxBundle.message("into.before.or.after.expected"));
    }
    marker.done(JavaFxElementTypes.INSERT_EXPRESSION);
  }

  private void parseIndexedSequenceForInsert() {
    if (!parsePrimaryExpression()) {
      myBuilder.error(JavaFxBundle.message("expression.expected"));
    }
    checkMatches(JavaFxTokenTypes.LBRACK, JavaFxBundle.message("lbrack.expected"));
    parseValueExpression();
    checkMatches(JavaFxTokenTypes.RBRACK, JavaFxBundle.message("rbrack.expected"));
  }

  private void parseDeleteExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.DELETE_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    parseValueExpression();
    if (atToken(JavaFxTokenTypes.FROM_KEYWORD)) {
      nextToken();
      parseValueExpression();
    }
    marker.done(JavaFxElementTypes.DELETE_EXPRESSION);
  }

  private void parseWhileExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.WHILE_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    checkMatches(JavaFxTokenTypes.LPAREN, JavaFxBundle.message("lparen.expected"));
    parseValueExpression();
    checkMatches(JavaFxTokenTypes.RPAREN, JavaFxBundle.message("rparen.expected"));
    parseExpression();
    marker.done(JavaFxElementTypes.WHILE_EXPRESSION);
  }

  private void parseBreakExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.BREAK_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    marker.done(JavaFxElementTypes.BREAK_EXPRESSION);
  }

  private void parseContinueExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.CONTINUE_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    marker.done(JavaFxElementTypes.CONTINUE_EXPRESSION);
  }

  private void parseThrowExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.THROW_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    parseValueExpression();
    marker.done(JavaFxElementTypes.THROW_EXPRESSION);
  }

  private void parseReturnExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.RETURN_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    if (!parseValueExpressionOptional()) {
      checkForSemicolon();
    }
    marker.done(JavaFxElementTypes.RETURN_EXPRESSION);
  }

  private void parseTryExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.TRY_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    parseBlockExpression();
    if (atToken(JavaFxTokenTypes.FINALLY_KEYWORD)) {
      parseFinallyClause();
    }
    else if (atToken(JavaFxTokenTypes.CATCH_KEYWORD)) {
      do {
        parseCatchClause();
      }
      while (atToken(JavaFxTokenTypes.CATCH_KEYWORD));
      if (atToken(JavaFxTokenTypes.FINALLY_KEYWORD)) {
        parseFinallyClause();
      }
    }
    else {
      myBuilder.error(JavaFxBundle.message("catch.or.finally.expected"));
    }
    marker.done(JavaFxElementTypes.TRY_EXPRESSION);
  }

  private void parseCatchClause() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.CATCH_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    checkMatches(JavaFxTokenTypes.LPAREN, JavaFxBundle.message("lparen.expected"));
    parseFormalParameter(true);
    checkMatches(JavaFxTokenTypes.RPAREN, JavaFxBundle.message("rparen.expected"));
    parseBlockExpression();
    marker.done(JavaFxElementTypes.CATCH_CLAUSE);
  }

  private void parseFinallyClause() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.FINALLY_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    parseBlockExpression();
    marker.done(JavaFxElementTypes.FINALLY_CLAUSE);
  }

  private void parseInvalidateExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.INVALIDATE_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    if (!parseValueExpressionOptional()) {
      marker.rollbackTo();
      parseValueExpression();
      return;
    }
    marker.done(JavaFxElementTypes.INVALIDATE_EXPRESSION);
  }

  // TODO: better parsing for function types
  private void parseFormalParameter(boolean checkParameterNames) {
    PsiBuilder.Marker marker = myBuilder.mark();
    if (checkParameterNames) {
      checkMatches(JavaFxTokenTypes.NAME, JavaFxBundle.message("name.expected"));
      parseTypeSpecifier();
    }
    else {
      if (atToken(JavaFxTokenTypes.COLON)) {
        parseTypeSpecifier();
      }
      else {
        parseType();
        if (atToken(JavaFxTokenTypes.COLON)) {
          marker.rollbackTo();
          marker = myBuilder.mark();
          nextToken();
          parseTypeSpecifier();
        }
      }
    }
    marker.done(JavaFxElementTypes.FORMAL_PARAMETER);
  }

  private void parseTypeSpecifier() {
    if (atToken(JavaFxTokenTypes.COLON)) {
      nextToken();
      parseType();
    }
  }

  private void parseType() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    if (atToken(JavaFxTokenTypes.FUNCTION_KEYWORD)) {
      nextToken();
      parseFunctionSignature(false);
      marker.done(JavaFxElementTypes.FUNCTION_TYPE_ELEMENT);
    }
    else {
      parseQualifiedName(JavaFxElementTypes.REFERENCE_ELEMENT);
      if (atToken(JavaFxTokenTypes.LBRACK)) {
        nextToken();
        checkMatches(JavaFxTokenTypes.RBRACK, JavaFxBundle.message("rbrack.expected"));
      }
      marker.done(JavaFxElementTypes.TYPE_ELEMENT);
    }
  }

  private void parseQualifiedName(final JavaFxElementType elementType) {
    if (!JavaFxTokenTypes.NAME.contains(myBuilder.getTokenType())) {
      myBuilder.error(JavaFxBundle.message("name.expected"));
      return;
    }
    PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    marker.done(elementType);
    while (atToken(JavaFxTokenTypes.DOT)) {
      nextToken();
      marker = createReferenceFragment(elementType, marker);
    }
  }

  private PsiBuilder.Marker createReferenceFragment(final JavaFxElementType elementType, PsiBuilder.Marker marker) {
    marker = marker.precede();
    if (atToken(JavaFxTokenTypes.THIS_KEYWORD)) {
      nextToken();
      marker.done(JavaFxElementTypes.THIS_EXPRESSION);
    }
    else {
      checkMatches(JavaFxTokenTypes.NAME_ALL, JavaFxBundle.message("name.expected"));
      marker.done(elementType);
    }
    return marker;
  }

  private void parseValueExpression() {
    if (!parseValueExpressionOptional()) {
      myBuilder.error(JavaFxBundle.message("expression.expected"));
    }
  }

  private boolean parseValueExpressionOptional() {
    final IElementType firstToken = myBuilder.getTokenType();
    if (firstToken == JavaFxTokenTypes.IF_KEYWORD) {
      parseIfExpression();
    }
    else if (firstToken == JavaFxTokenTypes.FOR_KEYWORD) {
      parseForExpression();
    }
    else if (firstToken == JavaFxTokenTypes.NEW_KEYWORD) {
      parseNewExpression();
    }
    else if (JavaFxTokenTypes.VARIABLE_LABEL.contains(firstToken)) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      parseModifiers();
      parseVariableDeclaration(marker);
    }
    else if (JavaFxTokenTypes.MODIFIERS.contains(firstToken)) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      parseModifiers();
      if (JavaFxTokenTypes.VARIABLE_LABEL.contains(myBuilder.getTokenType())) {
        parseVariableDeclaration(marker);
      }
      else {
        marker.drop();
        myBuilder.error(JavaFxBundle.message("var.or.def.expected"));
      }
    }
    else {
      return parseAssignmentExpression();
    }
    return true;
  }

  private void parseIfExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.IF_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    checkMatches(JavaFxTokenTypes.LPAREN, JavaFxBundle.message("lparen.expected"));
    parseValueExpression();
    checkMatches(JavaFxTokenTypes.RPAREN, JavaFxBundle.message("rparen.expected"));
    if (atToken(JavaFxTokenTypes.THEN_KEYWORD)) {
      nextToken();
    }
    parseExpression();
    if (atToken(JavaFxTokenTypes.ELSE_KEYWORD)) {
      nextToken();
      parseExpression();
    }
    marker.done(JavaFxElementTypes.IF_EXPRESSION);
  }

  private void parseForExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.FOR_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    checkMatches(JavaFxTokenTypes.LPAREN, JavaFxBundle.message("lparen.expected"));
    parseInClause();
    while (atToken(JavaFxTokenTypes.COMMA)) {
      nextToken();
      parseInClause();
    }
    checkMatches(JavaFxTokenTypes.RPAREN, JavaFxBundle.message("rparen.expected"));
    parseExpression();
    marker.done(JavaFxElementTypes.FOR_EXPRESSION);
  }

  private void parseInClause() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    parseFormalParameter(true);
    checkMatches(JavaFxTokenTypes.IN_KEYWORD, JavaFxBundle.message("in.expected"));
    parseValueExpression();
    if (atToken(JavaFxTokenTypes.WHERE_KEYWORD)) {
      nextToken();
      parseValueExpression();
    }
    marker.done(JavaFxElementTypes.IN_CLAUSE);
  }

  private void parseNewExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.NEW_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    parseQualifiedName(JavaFxElementTypes.REFERENCE_ELEMENT);
    if (atToken(JavaFxTokenTypes.LPAREN)) {
      parseExpressionList();
    }
    marker.done(JavaFxElementTypes.NEW_EXPRESSION);
  }

  private void parseVariableDeclaration(final PsiBuilder.Marker marker) {
    LOG.assertTrue(JavaFxTokenTypes.VARIABLE_LABEL.contains(myBuilder.getTokenType()));
    nextToken();
    checkMatches(JavaFxTokenTypes.NAME, JavaFxBundle.message("name.expected"));
    parseTypeSpecifier();
    if (atToken(JavaFxTokenTypes.EQ)) {
      nextToken();
      parseInitializingExpression();
    }
    if (atToken(JavaFxTokenTypes.ON_KEYWORD)) {
      parseOnClause();
    }
    marker.done(JavaFxElementTypes.VARIABLE_DECLARATION);
  }

  private void parseOnClause() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.ON_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    final IElementType firstToken = myBuilder.getTokenType();
    if (firstToken == JavaFxTokenTypes.REPLACE_KEYWORD) {
      parseOnReplaceClause(marker);
    }
    else if (firstToken == JavaFxTokenTypes.INVALIDATE_KEYWORD) {
      parseInvalidateClause(marker);
    }
    else {
      marker.drop();
      myBuilder.error(JavaFxBundle.message("replace.or.invalidate.expected"));
    }
  }

  private void parseOnReplaceClause(final PsiBuilder.Marker marker) {
    LOG.assertTrue(atToken(JavaFxTokenTypes.REPLACE_KEYWORD));
    nextToken();
    if (JavaFxTokenTypes.NAME.contains(myBuilder.getTokenType())) {
      nextToken();
    }
    if (atToken(JavaFxTokenTypes.LBRACK)) {
      nextToken();
      checkMatches(JavaFxTokenTypes.NAME, JavaFxBundle.message("name.expected"));
      checkMatches(JavaFxTokenTypes.RANGE, JavaFxBundle.message("range.expected"));
      checkMatches(JavaFxTokenTypes.NAME, JavaFxBundle.message("name.expected"));
      checkMatches(JavaFxTokenTypes.RBRACK, JavaFxBundle.message("rbrack.expected"));
    }
    if (atToken(JavaFxTokenTypes.EQ)) {
      nextToken();
      checkMatches(JavaFxTokenTypes.NAME, JavaFxBundle.message("name.expected"));
    }
    parseBlockExpression();
    marker.done(JavaFxElementTypes.ON_REPLACE_CLAUSE);
  }

  private void parseInvalidateClause(final PsiBuilder.Marker marker) {
    LOG.assertTrue(atToken(JavaFxTokenTypes.INVALIDATE_KEYWORD));
    nextToken();
    parseBlockExpression();
    marker.done(JavaFxElementTypes.ON_INVALIDATE_CLAUSE);
  }

  private boolean parseAssignmentExpression() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    if (!parseAssignmentOpExpression()) {
      marker.drop();
      return false;
    }
    if (atToken(JavaFxTokenTypes.EQ)) {
      nextToken();
      parseValueExpression();
      marker.done(JavaFxElementTypes.ASSIGNMENT_EXPRESSION);
    }
    else {
      marker.drop();
    }
    return true;
  }

  private boolean parseAssignmentOpExpression() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    if (!parseOrExpression()) {
      marker.drop();
      return false;
    }
    if (JavaFxTokenTypes.EQ_OPERATORS.contains(myBuilder.getTokenType())) {
      nextToken();
      parseValueExpression();
      marker.done(JavaFxElementTypes.ASSIGNMENT_EXPRESSION);
    }
    else if (atToken(JavaFxTokenTypes.EQGT)) {
      nextToken();
      if (!parseOrExpression()) {
        myBuilder.error(JavaFxBundle.message("expression.expected"));
      }
      if (atToken(JavaFxTokenTypes.TWEEN_KEYWORD)) {
        nextToken();
        if (!parseOrExpression()) {
          myBuilder.error(JavaFxBundle.message("expression.expected"));
        }
      }
      marker.done(JavaFxElementTypes.ASSIGNMENT_EXPRESSION);
    }
    else {
      marker.drop();
    }
    return true;
  }

  private boolean parseOrExpression() {
    PsiBuilder.Marker marker = myBuilder.mark();
    if (!parseAndExpression()) {
      marker.drop();
      return false;
    }
    while (atToken(JavaFxTokenTypes.OR_KEYWORD)) {
      nextToken();
      if (!parseAndExpression()) {
        myBuilder.error(JavaFxBundle.message("expression.expected"));
      }
      marker.done(JavaFxElementTypes.BINARY_EXPRESSION);
      marker = marker.precede();
    }
    marker.drop();
    return true;
  }

  private boolean parseAndExpression() {
    PsiBuilder.Marker marker = myBuilder.mark();
    if (!parseTypeExpression()) {
      marker.drop();
      return false;
    }
    while (atToken(JavaFxTokenTypes.AND_KEYWORD)) {
      nextToken();
      if (!parseTypeExpression()) {
        myBuilder.error(JavaFxBundle.message("expression.expected"));
      }
      marker.done(JavaFxElementTypes.BINARY_EXPRESSION);
      marker = marker.precede();
    }
    marker.drop();
    return true;
  }

  private boolean parseTypeExpression() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    if (!parseRelationalExpression()) {
      marker.drop();
      return false;
    }
    if (atToken(JavaFxTokenTypes.AS_KEYWORD) || atToken(JavaFxTokenTypes.INSTANCEOF_KEYWORD)) {
      nextToken();
      parseType();
      marker.done(JavaFxElementTypes.TYPE_EXPRESSION);
    }
    else {
      marker.drop();
    }
    return true;
  }

  private boolean parseRelationalExpression() {
    PsiBuilder.Marker marker = myBuilder.mark();
    if (!parseAdditiveExpression()) {
      marker.drop();
      return false;
    }
    while (JavaFxTokenTypes.RELATIONAL_OPERATORS.contains(myBuilder.getTokenType())) {
      nextToken();
      if (!parseAdditiveExpression()) {
        myBuilder.error(JavaFxBundle.message("expression.expected"));
      }
      marker.done(JavaFxElementTypes.BINARY_EXPRESSION);
      marker = marker.precede();
    }
    marker.drop();
    return true;
  }

  private boolean parseAdditiveExpression() {
    PsiBuilder.Marker marker = myBuilder.mark();
    if (!parseMultiplicativeExpression()) {
      marker.drop();
      return false;
    }
    while (atToken(JavaFxTokenTypes.PLUS) || atToken(JavaFxTokenTypes.MINUS)) {
      nextToken();
      if (!parseMultiplicativeExpression()) {
        myBuilder.error(JavaFxBundle.message("expression.expected"));
      }
      marker.done(JavaFxElementTypes.BINARY_EXPRESSION);
      marker = marker.precede();
    }
    marker.drop();
    return true;
  }

  private boolean parseMultiplicativeExpression() {
    PsiBuilder.Marker marker = myBuilder.mark();
    if (!parseUnaryExpression()) {
      marker.drop();
      return false;
    }
    while (atToken(JavaFxTokenTypes.MULT) || atToken(JavaFxTokenTypes.DIV) || atToken(JavaFxTokenTypes.MOD_KEYWORD)) {
      nextToken();
      if (!parseUnaryExpression()) {
        myBuilder.error("expression.expected");
      }
      marker.done(JavaFxElementTypes.BINARY_EXPRESSION);
      marker = marker.precede();
    }
    marker.drop();
    return true;
  }

  private boolean parseUnaryExpression() {
    if (atToken(JavaFxTokenTypes.INDEXOF_KEYWORD)) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      nextToken();
      checkMatches(JavaFxTokenTypes.IDENTIFIER, JavaFxBundle.message("name.expected"));
      marker.done(JavaFxElementTypes.INDEXOF_EXPRESSION);
      return true;
    }
    else if (JavaFxTokenTypes.UNARY_OPERATORS.contains(myBuilder.getTokenType())) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      nextToken();
      if (!parseUnaryExpression()) {
        myBuilder.error(JavaFxBundle.message("expression.expected"));
      }
      marker.done(JavaFxElementTypes.UNARY_EXPRESSION);
      return true;
    }
    return parseSuffixedExpression();
  }

  private boolean parseSuffixedExpression() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    if (!parsePostfixExpression()) {
      marker.drop();
      return false;
    }
    if (atToken(JavaFxTokenTypes.PLUSPLUS) || atToken(JavaFxTokenTypes.MINUSMINUS)) {
      nextToken();
      if (myPreviousTokenType == JavaFxTokenTypes.RBRACE) {
        myBuilder.error(JavaFxBundle.message("unexpected.token"));
      }
      marker.done(JavaFxElementTypes.SUFFIXED_EXPRESSION);
    }
    else {
      marker.drop();
    }
    return true;
  }

  private boolean parsePostfixExpression() {
    PsiBuilder.Marker marker = myBuilder.mark();
    if (!parsePrimaryExpression()) {
      marker.drop();
      return false;
    }
    while (true) {
      final JavaFxElementType expressionType;
      if (atToken(JavaFxTokenTypes.DOT)) {
        nextToken();
        final IElementType elementType = myBuilder.getTokenType();
        if (elementType == JavaFxTokenTypes.THIS_KEYWORD) {
          expressionType = JavaFxElementTypes.THIS_EXPRESSION;
        }
        else {
          checkMatches(JavaFxTokenTypes.NAME_ALL, JavaFxBundle.message("name.expected"));
          expressionType = JavaFxElementTypes.REFERENCE_EXPRESSION;
        }
      }
      else if (atToken(JavaFxTokenTypes.LPAREN)) {
        parseExpressionList();
        expressionType = JavaFxElementTypes.CALL_EXPRESSION;
      }
      else if (atToken(JavaFxTokenTypes.LBRACK)) {
        nextToken();
        parseValueExpression();
        if (atToken(JavaFxTokenTypes.RBRACK)) {
          nextToken();
          expressionType = JavaFxElementTypes.INDEX_EXPRESSION;
        }
        else if (atToken(JavaFxTokenTypes.DELIM)) {
          nextToken();
          parseValueExpression();
          checkMatches(JavaFxTokenTypes.RBRACK, JavaFxBundle.message("rbrack.expected"));
          expressionType = JavaFxElementTypes.SEQUENCE_SELECT_EXPRESSION;
        }
        else if (atToken(JavaFxTokenTypes.RANGE)) {
          nextToken();
          if (atToken(JavaFxTokenTypes.LT)) {
            nextToken();
          }
          if (!atToken(JavaFxTokenTypes.RBRACK)) {
            parseValueExpression();
          }
          checkMatches(JavaFxTokenTypes.RBRACK, JavaFxBundle.message("rbrack.expected"));
          expressionType = JavaFxElementTypes.SLICE_EXPRESSION;
        }
        else {
          myBuilder.error(JavaFxBundle.message("range.delim.or.rbrack.expected"));
          marker.drop();
          return false;
        }
      }
      else {
        break;
      }
      marker.done(expressionType);
      marker = marker.precede();
    }
    marker.drop();
    return true;
  }

  private void parseExpressionList() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.LPAREN));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    if (atToken(JavaFxTokenTypes.RPAREN)) {
      nextToken();
      marker.done(JavaFxElementTypes.EXPRESSION_LIST);
      return;
    }
    parseValueExpression();
    while (atToken(JavaFxTokenTypes.COMMA)) {
      nextToken();
      if (atToken(JavaFxTokenTypes.RPAREN)) {
        break;
      }
      parseValueExpression();
    }
    checkMatches(JavaFxTokenTypes.RPAREN, JavaFxBundle.message("rparen.expected"));
    marker.done(JavaFxElementTypes.EXPRESSION_LIST);
  }

  private boolean parsePrimaryExpression() {
    final IElementType firstToken = myBuilder.getTokenType();
    if (JavaFxTokenTypes.NAME.contains(firstToken)) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      parseQualifiedName(JavaFxElementTypes.REFERENCE_EXPRESSION);
      if (atToken(JavaFxTokenTypes.LBRACE)) {
        parseObjectLiteral(marker);
      }
      else {
        marker.drop();
      }
    }
    else if (firstToken == JavaFxTokenTypes.LBRACE) {
      parseBlockExpression();
    }
    else if (firstToken == JavaFxTokenTypes.THIS_KEYWORD) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      nextToken();
      marker.done(JavaFxElementTypes.THIS_EXPRESSION);
    }
    else if (firstToken == JavaFxTokenTypes.LBRACK) {
      parseSequenceOrRangeExpression();
    }
    else if (firstToken == JavaFxTokenTypes.LPAREN) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      nextToken();
      if (!parseValueExpressionOptional()) {
        marker.drop();
        return false;
      }
      checkMatches(JavaFxTokenTypes.RPAREN, JavaFxBundle.message("rparen.expected"));
      marker.done(JavaFxElementTypes.PARENTHESIZED_EXPRESSION);
    }
    else if (firstToken == JavaFxTokenTypes.AT_KEYWORD) {
      parseTimelineExpression();
    }
    else if (JavaFxTokenTypes.STRING_START.contains(firstToken)) {
      parseStringExpression();
    }
    else if (JavaFxTokenTypes.LITERALS.contains(firstToken)) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      nextToken();
      marker.done(JavaFxElementTypes.LITERAL_EXPRESSION);
    }
    else if (firstToken == JavaFxTokenTypes.FUNCTION_KEYWORD) {
      parseFunctionExpression();
    }
    else {
      return false;
    }
    return true;
  }

  private void parseStringExpression() {
    LOG.assertTrue(JavaFxTokenTypes.STRING_START.contains(myBuilder.getTokenType()));
    final PsiBuilder.Marker marker = myBuilder.mark();
    if (atToken(JavaFxTokenTypes.LOCALIZATION_PREFIX)) {
      nextToken();
      if (!JavaFxTokenTypes.STRING_START.contains(myBuilder.getTokenType())) {
        myBuilder.error(JavaFxBundle.message("string.expected"));
        marker.done(JavaFxElementTypes.STRING_EXPRESSION);
        return;
      }
    }
    do {
      parseStringCompoundElement();
    }
    while (JavaFxTokenTypes.STRING_START.contains(myBuilder.getTokenType()));
    marker.done(JavaFxElementTypes.STRING_EXPRESSION);
  }

  private void parseStringCompoundElement() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    IElementType firstToken = myBuilder.getTokenType();
    while (firstToken == JavaFxTokenTypes.LBRACE_STRING_LITERAL || firstToken == JavaFxTokenTypes.LBRACE_RBRACE_STRING_LITERAL) {
      nextToken();
      parseValueExpression();
      firstToken = myBuilder.getTokenType();
    }
    checkMatches(JavaFxTokenTypes.STRINGS, JavaFxBundle.message("string.expected"));
    marker.done(JavaFxElementTypes.STRING_ELEMENT);
  }

  private void parseFunctionExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.FUNCTION_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    parseFunctionSignature(true);
    parseBlockExpression();
    marker.done(JavaFxElementTypes.FUNCTION_EXPRESSION);
  }

  private void parseFunctionSignature(final boolean checkParameterNames) {
    final PsiBuilder.Marker marker = myBuilder.mark();
    parseFormalParameters(checkParameterNames);
    parseTypeSpecifier();
    marker.done(JavaFxElementTypes.FUNCTION_SIGNATURE);
  }

  private void parseFormalParameters(boolean checkParameterNames) {
    final PsiBuilder.Marker marker = myBuilder.mark();
    checkMatches(JavaFxTokenTypes.LPAREN, JavaFxBundle.message("lparen.expected"));
    if (atToken(JavaFxTokenTypes.RPAREN)) {
      nextToken();
      marker.done(JavaFxElementTypes.PARAMETER_LIST);
      return;
    }
    parseFormalParameter(checkParameterNames);
    while (atToken(JavaFxTokenTypes.COMMA)) {
      nextToken();
      if (atToken(JavaFxTokenTypes.RPAREN)) {
        break;
      }
      parseFormalParameter(checkParameterNames);
    }
    checkMatches(JavaFxTokenTypes.RPAREN, JavaFxBundle.message("rparen.or.comma.expected"));
    marker.done(JavaFxElementTypes.PARAMETER_LIST);
  }

  private void parseSequenceOrRangeExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.LBRACK));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    if (atToken(JavaFxTokenTypes.RBRACK)) {
      nextToken();
      marker.done(JavaFxElementTypes.SEQUENCE_LITERAL);
      return;
    }

    parseValueExpression();
    if (atToken(JavaFxTokenTypes.RANGE)) {
      nextToken();
      if (atToken(JavaFxTokenTypes.LT)) {
        nextToken();
      }
      parseValueExpression();
      if (atToken(JavaFxTokenTypes.STEP_KEYWORD)) {
        nextToken();
        parseValueExpression();
      }
      checkMatches(JavaFxTokenTypes.RBRACK, JavaFxBundle.message("rbrack.expected"));
      marker.done(JavaFxElementTypes.RANGE_EXPRESSION);
    }
    else {
      while (!atToken(JavaFxTokenTypes.RBRACK)) {
        if (myBuilder.eof()) {
          myBuilder.error(JavaFxBundle.message("rbrack.expected"));
          marker.done(JavaFxElementTypes.SEQUENCE_LITERAL);
          return;
        }
        if (atToken(JavaFxTokenTypes.COMMA)) {
          nextToken();
          if (atToken(JavaFxTokenTypes.RBRACK)) {
            break;
          }
        }
        else {
          if (myPreviousTokenType != JavaFxTokenTypes.RBRACE) {
            myBuilder.error(JavaFxBundle.message("rbrack.or.comma.expected"));
          }
        }
        if (!parseValueExpressionOptional()) {
          myBuilder.error(JavaFxBundle.message("rbrack.or.comma.expected"));
          break;
        }
      }
      checkMatches(JavaFxTokenTypes.RBRACK, JavaFxBundle.message("rbrack.expected"));
      marker.done(JavaFxElementTypes.SEQUENCE_LITERAL);
    }
  }

  private void parseTimelineExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.AT_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    checkMatches(JavaFxTokenTypes.LPAREN, JavaFxBundle.message("lparen.expected"));
    checkMatches(JavaFxTokenTypes.DURATION_LITERAL, JavaFxBundle.message("time.literal.expected"));
    checkMatches(JavaFxTokenTypes.RPAREN, JavaFxBundle.message("rparen.expected"));
    checkMatches(JavaFxTokenTypes.LBRACE, JavaFxBundle.message("lbrace.expected"));
    do {
      if (myBuilder.eof()) {
        myBuilder.error(JavaFxBundle.message("rbrace.expected"));
        marker.done(JavaFxElementTypes.TIMELINE_EXPRESSION);
        return;
      }
      if (atToken(JavaFxTokenTypes.SEMICOLON)) {
        nextToken();
      }
      else {
        parseValueExpression();
      }
    }
    while (!atToken(JavaFxTokenTypes.RBRACE));
    nextToken();
    marker.done(JavaFxElementTypes.TIMELINE_EXPRESSION);
  }

  private void parseObjectLiteral(final PsiBuilder.Marker marker) {
    nextToken();
    while (true) {
      if (atToken(JavaFxTokenTypes.COMMA) || atToken(JavaFxTokenTypes.SEMICOLON)) {
        do {
          nextToken();
        }
        while (atToken(JavaFxTokenTypes.COMMA) || atToken(JavaFxTokenTypes.SEMICOLON));
      }
      if (JavaFxTokenTypes.NAME.contains(myBuilder.getTokenType())) {
        final PsiBuilder.Marker objectLiteralInit = myBuilder.mark();
        parseQualifiedName(JavaFxElementTypes.REFERENCE_ELEMENT);
        checkMatches(JavaFxTokenTypes.COLON, JavaFxBundle.message("colon.expected"));
        parseInitializingExpression();
        objectLiteralInit.done(JavaFxElementTypes.OBJECT_LITERAL_INIT);
      }
      else if (JavaFxTokenTypes.VARIABLE_LABEL.contains(myBuilder.getTokenType())) {
        final PsiBuilder.Marker variableMarker = myBuilder.mark();
        parseModifiers();
        parseVariableDeclaration(variableMarker);
      }
      else if (atToken(JavaFxTokenTypes.FUNCTION_KEYWORD)) {
        final PsiBuilder.Marker functionMarker = myBuilder.mark();
        parseModifiers();
        parseFunctionDefinition(functionMarker);
      }
      else if (JavaFxTokenTypes.MODIFIERS.contains(myBuilder.getTokenType())) {
        final PsiBuilder.Marker definitionMarker = myBuilder.mark();
        parseModifiers();
        if (atToken(JavaFxTokenTypes.FUNCTION_KEYWORD)) {
          parseFunctionDefinition(definitionMarker);
        }
        else if (JavaFxTokenTypes.VARIABLE_LABEL.contains(myBuilder.getTokenType())) {
          parseVariableDeclaration(definitionMarker);
        }
        else {
          definitionMarker.drop();
          myBuilder.error(JavaFxBundle.message("unexpected.token"));
        }
      }
      else if (atToken(JavaFxTokenTypes.RBRACE)) {
        nextToken();
        break;
      }
      else {
        myBuilder.error(JavaFxBundle.message("rbrace.expected"));
        break;
      }
    }
    marker.done(JavaFxElementTypes.OBJECT_LITERAL);
  }

  private void parseInitializingExpression() {
    if (atToken(JavaFxTokenTypes.BIND_KEYWORD)) {
      parseBoundExpression();
    }
    else {
      parseValueExpression();
    }
  }

  private void parseBoundExpression() {
    LOG.assertTrue(atToken(JavaFxTokenTypes.BIND_KEYWORD));
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    parseValueExpression();
    if (atToken(JavaFxTokenTypes.WITH_KEYWORD)) {
      nextToken();
      checkMatches(JavaFxTokenTypes.INVERSE_KEYWORD, JavaFxBundle.message("inverse.expected"));
    }
    marker.done(JavaFxElementTypes.BOUND_EXPRESSION);
  }

  private void parseBlockExpression() {
    if (!atToken(JavaFxTokenTypes.LBRACE)) {
      myBuilder.error(JavaFxBundle.message("lbrace.expected"));
      return;
    }
    final PsiBuilder.Marker marker = myBuilder.mark();
    nextToken();
    while (!atToken(JavaFxTokenTypes.RBRACE)) {
      if (myBuilder.eof()) {
        myBuilder.error(JavaFxBundle.message("rbrace.expected"));
        marker.done(JavaFxElementTypes.BLOCK_EXPRESSION);
        return;
      }
      if (atToken(JavaFxTokenTypes.SEMICOLON)) {
        nextToken();
      }
      else {
        parseExpression();
        checkForSemicolon();
      }
    }
    nextToken();
    marker.done(JavaFxElementTypes.BLOCK_EXPRESSION);
  }
}
