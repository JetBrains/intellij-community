package ru.adelf.idea.dotenv.psi;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.*;
import ru.adelf.idea.dotenv.DotEnvLanguage;

public class DotEnvElementType extends IElementType {
    public DotEnvElementType(@NotNull @NonNls String debugName) {
        super(debugName, DotEnvLanguage.INSTANCE);
    }
}