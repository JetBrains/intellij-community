package org.netbeans.lib.cvsclient.command.checkout;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IRequestProcessor;
import org.netbeans.lib.cvsclient.command.AbstractMessageParser;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IEventSender;
import org.netbeans.lib.cvsclient.file.DirectoryObject;
import org.netbeans.lib.cvsclient.progress.IProgressViewer;
import org.netbeans.lib.cvsclient.progress.sending.DummyRequestsProgressHandler;
import org.netbeans.lib.cvsclient.request.CommandRequest;
import org.netbeans.lib.cvsclient.request.Requests;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Olesya
 */
public class ListModulesCommand extends Command {

  // Fields =================================================================

  private final Set<Module> modules = new HashSet<>();
  private Module lastModule;
  private Consumer<Module> myModuleConsumer;

  // Setup ==================================================================

  public ListModulesCommand() {
    getGlobalOptions().setDoNoChanges(true);
  }

  // Implemented ============================================================

  @Override
  public boolean execute(IRequestProcessor requestProcessor, IEventSender eventManager,
                         ICvsListenerRegistry listenerRegistry,
                         IClientEnvironment clientEnvironment,
                         IProgressViewer progressViewer) throws CommandException, AuthenticationException {
    modules.clear();

    final Requests requests = new Requests(CommandRequest.CHECKOUT, clientEnvironment);
    requests.addArgumentRequest("-N");
    requests.addArgumentRequest("-c");
    requests.addDirectoryRequest(DirectoryObject.createInstance("/"));

    final ICvsListener listener = new GetModulesParser();
    listener.registerListeners(listenerRegistry);
    try {
      return requestProcessor.processRequests(requests, new DummyRequestsProgressHandler());
    }
    finally {
      listener.unregisterListeners(listenerRegistry);
    }
  }

  @Override
  public String getCvsCommandLine() {
    return null;
  }

  // Accessing ==============================================================

  public Set<Module> getModules() {
    return modules;
  }

  // Utils ==================================================================

  @Nullable
  private static String trimMaybeNull(String string) {
    if (string == null) {
      return null;
    }
    string = string.trim();
    if (string.isEmpty()) {
      return null;
    }
    return string;
  }

  // Inner classes ==========================================================

  private final class GetModulesParser extends AbstractMessageParser {
    private static final String MUTILINE_MODULE_PREFIX = " ";

    @Override
    public void parseLine(String line, boolean errorMessage) {
      if (errorMessage) {
        return;
      }

      if (line.startsWith(MUTILINE_MODULE_PREFIX)) {
        if (lastModule != null) {
          lastModule.appendOptions(line.trim());
        }
      }

      line = line.replace('\t', ' ');
      final int spaceIndex = line.indexOf(' ');
      if (spaceIndex < 0) {
        return;
      }

      final String moduleName = trimMaybeNull(line.substring(0, spaceIndex));
      if (moduleName == null) {
        return;
      }

      final String options = trimMaybeNull(line.substring(spaceIndex));
      if (options != null) {
        addNewModule(new Module(moduleName, options));
      }
      else {
        addNewModule(new Module(moduleName));
      }
    }

    private void addNewModule(Module module) {
      if (myModuleConsumer != null) {
        myModuleConsumer.consume(module);
      }
      modules.add(module);
      lastModule = module;
    }

    @Override
    public void binaryMessageSent(final byte[] bytes) {}

    @Override
    public void outputDone() {}
  }

  public void setModuleConsumer(Consumer<Module> moduleConsumer) {
    myModuleConsumer = moduleConsumer;
  }
}
