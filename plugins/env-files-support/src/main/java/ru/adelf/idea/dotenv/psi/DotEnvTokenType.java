package ru.adelf.idea.dotenv.psi;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.DotEnvLanguage;

public class DotEnvTokenType extends IElementType {
    public DotEnvTokenType(@NotNull @NonNls String debugName) {
        super(debugName, DotEnvLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "DotEnvTokenType." + super.toString();
    }
}
