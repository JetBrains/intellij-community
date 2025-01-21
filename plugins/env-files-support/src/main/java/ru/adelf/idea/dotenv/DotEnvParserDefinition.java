package ru.adelf.idea.dotenv;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.*;
import ru.adelf.idea.dotenv.grammars.DotEnvLexerAdapter;
import ru.adelf.idea.dotenv.parser.DotEnvParser;
import ru.adelf.idea.dotenv.psi.*;
import org.jetbrains.annotations.NotNull;

public class DotEnvParserDefinition implements ParserDefinition {
    private static final TokenSet WHITE_SPACES = TokenSet.WHITE_SPACE;
    private static final IFileElementType FILE = new IFileElementType(DotEnvLanguage.INSTANCE);

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new DotEnvLexerAdapter();
    }

    @Override
    @NotNull
    public TokenSet getWhitespaceTokens() {
        return WHITE_SPACES;
    }

    @Override
    @NotNull
    public TokenSet getCommentTokens() {
        return TokenSet.create(DotEnvTypes.COMMENT);
    }

    @Override
    @NotNull
    public TokenSet getStringLiteralElements() {
        return TokenSet.EMPTY;
    }

    @Override
    @NotNull
    public PsiParser createParser(final Project project) {
        return new DotEnvParser();
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new DotEnvFile(viewProvider);
    }

    @Override
    public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }

    @Override
    @NotNull
    public PsiElement createElement(ASTNode node) {
        return DotEnvTypes.Factory.createElement(node);
    }
}
