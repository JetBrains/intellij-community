package org.jetbrains.android.compiler.tools;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.util.ExecutionUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

/**
 * IDL compiler.
 *
 * @author Alexey Efimov
 */
public final class AndroidIdl {
  private AndroidIdl() {
  }

  @NotNull
  public static Map<CompilerMessageCategory, List<String>> execute(@NotNull IAndroidTarget target,
                                                                   @NotNull String file,
                                                                   @NotNull String outFile,
                                                                   @NotNull VirtualFile[] sourceRoots) throws IOException {
    List<String> commands = new ArrayList<String>();
    String frameworkAidlPath = target.getPath(IAndroidTarget.ANDROID_AIDL);
    commands.add(target.getPath(IAndroidTarget.AIDL));
    commands.add("-p" + frameworkAidlPath);
    for (VirtualFile root : sourceRoots) {
      commands.add("-I" + root.getPath());
    }
    commands.add(file);
    commands.add(outFile);
    return ExecutionUtil.execute(ArrayUtil.toStringArray(commands));
  }

}
