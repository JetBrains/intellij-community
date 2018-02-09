package ru.adelf.idea.dotenv;

import com.intellij.lexer.FlexAdapter;
import ru.adelf.idea.dotenv.grammars._DotEnvLexer;

class DotEnvLexerAdapter extends FlexAdapter {
    DotEnvLexerAdapter() {
        super(new _DotEnvLexer(null));
    }
}