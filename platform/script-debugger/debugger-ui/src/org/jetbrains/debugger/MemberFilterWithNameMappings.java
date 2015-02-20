package org.jetbrains.debugger;

import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class MemberFilterWithNameMappings extends MemberFilterBase {
  protected final Map<String, String> rawNameToSource;

  public MemberFilterWithNameMappings(@NotNull Map<String, String> rawNameToSource) {
    this.rawNameToSource = rawNameToSource;
  }

  @Override
  public final boolean hasNameMappings() {
    return !rawNameToSource.isEmpty();
  }

  @NotNull
  @Override
  public String getName(@NotNull Variable variable) {
    String name = variable.getName();
    return ObjectUtils.chooseNotNull(sourceNameToRaw(name), name);
  }

  @Nullable
  @Override
  public String sourceNameToRaw(@NotNull String name) {
    if (!hasNameMappings()) {
      return null;
    }

    for (Map.Entry<String, String> entry : rawNameToSource.entrySet()) {
      if (entry.getValue().equals(name)) {
        return entry.getKey();
      }
    }
    return null;
  }
}
