package ru.adelf.idea.dotenv;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.grammars.DotEnvLexerAdapter;
import ru.adelf.idea.dotenv.parser.DotEnvParser;
import ru.adelf.idea.dotenv.psi.DotEnvFile;
import ru.adelf.idea.dotenv.psi.DotEnvTypes;

public class DotEnvParserDefinition implements ParserDefinition {
    private static final TokenSet WHITE_SPACES = TokenSet.WHITE_SPACE;
    private static final IFileElementType FILE = new IFileElementType(DotEnvLanguage.INSTANCE);

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new DotEnvLexerAdapter();
    }

    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return WHITE_SPACES;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return TokenSet.create(DotEnvTypes.COMMENT);
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return TokenSet.EMPTY;
    }

    @Override
    public @NotNull PsiParser createParser(final Project project) {
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
    public @NotNull PsiElement createElement(ASTNode node) {
        return DotEnvTypes.Factory.createElement(node);
    }
}
