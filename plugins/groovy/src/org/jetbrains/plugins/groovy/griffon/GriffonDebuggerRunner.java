package org.jetbrains.plugins.groovy.griffon;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
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
                                                         Executor executor,
                                                         RunProfileState state,
                                                         RunContentDescriptor contentToReuse,
                                                         ExecutionEnvironment env) throws ExecutionException {
    final JavaCommandLine javaCommandLine = (JavaCommandLine)state;
    final JavaParameters params = javaCommandLine.getJavaParameters();
    //taken from griffonDebug.bat
    params.getVMParametersList().addParametersString("-Xdebug -Xnoagent -Dgriffon.full.stacktrace=true -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005");

    final boolean useSockets = true;
    String address = "";
    try {
      address = DebuggerUtils.getInstance().findAvailableDebugAddress(useSockets);

      for (String s : params.getProgramParametersList().getList()) {
        if (s.startsWith("run-")) {
          params.getProgramParametersList().replaceOrAppend(s, s + " --debug --debugPort=" + address);
          break;
        }
      }
    }
    catch (ExecutionException ignored) {
    }

    RemoteConnection connection = new RemoteConnection(useSockets, "127.0.0.1", address, false);
    return attachVirtualMachine(project, executor, state, contentToReuse, env, connection, true);
  }

}
