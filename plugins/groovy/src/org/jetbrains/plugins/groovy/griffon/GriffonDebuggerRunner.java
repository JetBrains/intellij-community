package org.jetbrains.plugins.groovy.griffon;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class GriffonDebuggerRunner extends GenericDebuggerRunner {
  public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile) {
    return executorId.equals(DefaultDebugExecutor.EXECUTOR_ID) && profile instanceof GriffonRunConfiguration;
  }

  @NotNull
  public String getRunnerId() {
    return "GriffonDebugger";
  }


  @Override
  protected RunContentDescriptor createContentDescriptor(Project project,
                                                         RunProfileState state,
                                                         RunContentDescriptor contentToReuse,
                                                         ExecutionEnvironment env) throws ExecutionException {
    final JavaCommandLine javaCommandLine = (JavaCommandLine)state;
    final JavaParameters params = javaCommandLine.getJavaParameters();

    if (!params.getVMParametersList().hasProperty("griffon.full.stacktrace")) {
      params.getVMParametersList().add("-Dgriffon.full.stacktrace=true");
    }

    String address = null;
    try {
      for (String s : params.getProgramParametersList().getList()) {
        if (s.startsWith("run-")) {
          // Application will be run in forked VM
          address = DebuggerUtils.getInstance().findAvailableDebugAddress(true);
          params.getProgramParametersList().replaceOrAppend(s, s + " --debug --debugPort=" + address);
          break;
        }
      }
    }
    catch (ExecutionException ignored) {
    }

    if (address == null) {
      return super.createContentDescriptor(project, state, contentToReuse, env);
    }

    RemoteConnection connection = new RemoteConnection(true, "127.0.0.1", address, false);
    return attachVirtualMachine(project, state, contentToReuse, env, connection, true);
  }

}
