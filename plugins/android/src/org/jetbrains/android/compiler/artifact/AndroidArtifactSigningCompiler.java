package org.jetbrains.android.compiler.artifact;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.PackagingCompiler;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidArtifactSigningCompiler implements PackagingCompiler {
  @Override
  public void processOutdatedItem(CompileContext context, String url, @Nullable ValidityState state) {
  }

  @NotNull
  @Override
  public ProcessingItem[] getProcessingItems(CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new Computable<ProcessingItem[]>() {
      @Override
      public ProcessingItem[] compute() {
        return null;
      }
    });
  }

  @Override
  public ProcessingItem[] process(CompileContext context, ProcessingItem[] items) {
    return new ProcessingItem[0];  //To change body of implemented methods use File | Settings | File Templates.
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android Artifact Signing Compiler";
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Override
  public ValidityState createValidityState(DataInput in) throws IOException {
    return new MyValidityState(in);
  }

  private static class MyProcessingItem implements ProcessingItem {
    private final VirtualFile myApkFile;
    private final AndroidArtifactSigningMode mySigningMode;
    private final String myDebugKeyStorePath;
    private final MyValidityState myValidityState;

    private MyProcessingItem(@NotNull VirtualFile apkFile,
                             @NotNull AndroidArtifactSigningMode signingMode,
                             @Nullable String debugKeyStorePath) {
      myApkFile = apkFile;
      mySigningMode = signingMode;
      myDebugKeyStorePath = debugKeyStorePath;

      myValidityState = new MyValidityState(myApkFile.getModificationStamp(),
                                            mySigningMode.name(),
                                            myDebugKeyStorePath);
    }

    @NotNull
    @Override
    public VirtualFile getFile() {
      return myApkFile;
    }

    @Override
    public ValidityState getValidityState() {
      return myValidityState;
    }
  }

  private static class MyValidityState implements ValidityState {
    private final long myApkFileTimestamp;
    private final String mySigningMode;
    private final String myDebugKeyStorePath;

    private MyValidityState(long apkFileTimestamp, @NotNull String signingMode, @NotNull String debugKeyStorePath) {
      myApkFileTimestamp = apkFileTimestamp;
      mySigningMode = signingMode;
      myDebugKeyStorePath = debugKeyStorePath;
    }

    private MyValidityState(@NotNull DataInput in) throws IOException {
      myApkFileTimestamp = in.readLong();
      mySigningMode = in.readUTF();
      myDebugKeyStorePath = in.readUTF();
    }

    @Override
    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValidityState)) {
        return false;
      }
      final MyValidityState state = (MyValidityState)otherState;
      return state.myApkFileTimestamp == myApkFileTimestamp &&
             state.mySigningMode.equals(mySigningMode) &&
             state.myDebugKeyStorePath.equals(myDebugKeyStorePath);
    }

    @Override
    public void save(DataOutput out) throws IOException {
      out.writeLong(myApkFileTimestamp);
      out.writeUTF(mySigningMode);
      out.writeUTF(myDebugKeyStorePath);
    }
  }
}
