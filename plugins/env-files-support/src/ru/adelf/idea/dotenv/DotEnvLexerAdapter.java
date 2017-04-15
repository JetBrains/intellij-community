package ru.adelf.idea.dotenv;

import com.intellij.lexer.FlexAdapter;
import ru.adelf.idea.dotenv.grammars._DotEnvLexer;

import java.io.Reader;

public class DotEnvLexerAdapter extends FlexAdapter {
    public DotEnvLexerAdapter() {
        super(new _DotEnvLexer((Reader) null));
    }
}