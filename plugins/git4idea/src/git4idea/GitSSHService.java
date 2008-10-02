package git4idea;

import com.intellij.ide.XmlRpcServer;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.trilead.ssh2.KnownHosts;
import gnu.trove.THashMap;
import org.apache.commons.codec.DecoderException;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.git4idea.ssh.GitSSHHandler;
import org.jetbrains.git4idea.ssh.SSHMain;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;
import java.util.Vector;

/**
 * The provider of SSH scripts for the Git
 */
public class GitSSHService implements ApplicationComponent {
  /**
   * The string used to indicate missing value
   */
  private static final String XML_RPC_NULL_STRING = "\u0000";
  /**
   * the logger
   */
  private static final Logger log = Logger.getInstance(GitSSHService.class.getName());

  /**
   * random number generator to use
   */
  private static final Random RANDOM = new Random();
  /**
   * Name of the handler
   */
  @NonNls private static final String HANDLER_NAME = "Git4ideaSSHHandler";
  /**
   * The prefix of the ssh script name
   */
  @NonNls private static final String GIT_SSH_PREFIX = "git-ssh-";
  /**
   * The extension of the ssh script name
   */
  @NonNls private static final String GIT_SSH_EXT;

  static {
    if (SystemInfo.isWindows) {
      GIT_SSH_EXT = ".cmd";
    }
    else {
      GIT_SSH_EXT = ".sh";
    }
  }

  /**
   * Path to the generated script
   */
  private File myScriptPath;
  /**
   * XML rcp server
   */
  private final XmlRpcServer myXmlRpcServer;
  /**
   * Registered handlers
   */
  private final THashMap<Integer, Handler> handlers = new THashMap<Integer, Handler>();

  /**
   * Name of environment variable for SSH handler
   */
  @NonNls public static final String SSH_HANDLER_ENV = "GIT4IDEA_SSH_HANDLER";


  /**
   * A constructor from parameter
   *
   * @param xmlRpcServer the injected XmlRcp server reference
   */
  public GitSSHService(final @NotNull XmlRpcServer xmlRpcServer) {
    myXmlRpcServer = xmlRpcServer;
  }

  /**
   * @return an instance of the server
   */
  @NotNull
  public static GitSSHService getInstance() {
    final GitSSHService service = ServiceManager.getService(GitSSHService.class);
    if (service == null) {
      throw new IllegalStateException("The service " + GitSSHService.class.getName() + " cannot be located");
    }
    return service;
  }

  /**
   * Get file to the script service
   *
   * @return path to the script
   * @throws IOException
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  @NotNull
  public synchronized File getScriptPath() throws IOException {
    myXmlRpcServer.addHandler(HANDLER_NAME, new InternalRequestHandler());
    if (myScriptPath == null) {
      myScriptPath = File.createTempFile(GIT_SSH_PREFIX, GIT_SSH_EXT);
      myScriptPath.deleteOnExit();
      PrintWriter out = new PrintWriter(new FileWriter(myScriptPath));
      try {
        if (SystemInfo.isWindows) {
          out.println("@echo off");
        }
        else {
          out.println("#!/bin/sh");
        }
        String mainPath = PathUtil.getJarPathForClass(SSHMain.class);
        String sshPath = PathUtil.getJarPathForClass(KnownHosts.class);
        String xmlRcpPath = PathUtil.getJarPathForClass(XmlRpcClientLite.class);
        String codecPath = PathUtil.getJarPathForClass(DecoderException.class);
        String utilPath = PathUtil.getJarPathForClass(FileUtil.class);
        // six parameters are enough for the git case (actually 4 are enough)
        out.println("java -cp \"" +
                    mainPath +
                    File.pathSeparator +
                    sshPath +
                    File.pathSeparator +
                    codecPath +
                    File.pathSeparator +
                    xmlRcpPath +
                    File.pathSeparator +
                    utilPath +
                    "\" " +
                    SSHMain.class.getName() +
                    " " +
                    myXmlRpcServer.getPortNumber() +
                    " %1 %2 %3 %4 %5 %6");
      }
      finally {
        out.close();
      }
      FileUtil.setExectuableAttribute(myScriptPath.getAbsolutePath(), true);
    }
    return myScriptPath;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public String getComponentName() {
    return GitSSHService.class.getSimpleName();
  }

  /**
   * {@inheritDoc}
   */
  public void initComponent() {
    myXmlRpcServer.addHandler(HANDLER_NAME, new InternalRequestHandler());
    // do nothing
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void disposeComponent() {
    myXmlRpcServer.removeHandler(HANDLER_NAME);
    if (myScriptPath != null) {
      if (!myScriptPath.delete()) {
        log.warn("The temporary file " + myScriptPath + " generated by git4idea plugin failed to be removed during disposing.");
      }
      myScriptPath = null;
    }
  }

  /**
   * Register handler. Note that handlers must be unregistered using {@link #unregisterHander(int)}.
   *
   * @return an identifier to pass to the environment variable
   */
  public synchronized int registerHandler(@NotNull Handler handler) {
    while (true) {
      int rnd = RANDOM.nextInt();
      if (rnd == Integer.MIN_VALUE) {
        continue;
      }
      rnd = Math.abs(rnd);
      if (handlers.containsKey(rnd)) {
        continue;
      }
      handlers.put(rnd, handler);
      return rnd;
    }
  }

  /**
   * Get handler for the key
   *
   * @param key the key to use
   * @return the registered handler
   */
  @NotNull
  private synchronized Handler getHandler(int key) {
    Handler rc = handlers.get(key);
    if (rc == null) {
      throw new IllegalStateException("No handler for the key " + key);
    }
    return rc;
  }

  /**
   * Unregister handler by the key
   *
   * @param key the key to unregister
   */
  public synchronized void unregisterHander(int key) {
    if (handlers.remove(key) == null) {
      throw new IllegalArgumentException("The handler " + key + " is not registered");
    }
  }


  /**
   * Handler interface to use by the client code
   */
  public interface Handler {
    /**
     * Verify key
     *
     * @param hostname               a host name
     * @param port                   a port number
     * @param serverHostKeyAlgorithm an algorithm
     * @param serverHostKey          a key
     * @param isNew                  a isNew key
     * @return true if the key is valid
     */
    boolean verifyServerHostKey(final String hostname,
                                final int port,
                                final String serverHostKeyAlgorithm,
                                final String serverHostKey,
                                final boolean isNew);

    /**
     * Ask passphrase
     *
     * @param username  a user name
     * @param keyPath   a key path
     * @param lastError
     * @return a passphrase or null if dialog was cancelled.
     */
    String askPassphrase(final String username, final String keyPath, final String lastError);

    /**
     * Reply to challenge in keyboard-interactive scenario
     *
     * @param username
     * @param name        a name of challenge
     * @param instruction a instructions
     * @param numPrompts  number of prompts
     * @param prompt      prompts
     * @param echo        true if the reply for correponding prompt should be echoed
     * @param lastError
     * @return replies to the challenges
     */
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    Vector<String> replyToChallenge(final String username,
                                    final String name,
                                    final String instruction,
                                    final int numPrompts,
                                    final Vector<String> prompt,
                                    final Vector<Boolean> echo,
                                    final String lastError);

    /**
     * Ask password
     *
     * @param username  a user name
     * @param lastError
     * @return a password or null if dialog was cancelled.
     */
    String askPassword(final String username, final String lastError);

  }

  /**
   * Internal handler implemenation class, do not use it.
   */
  public class InternalRequestHandler implements GitSSHHandler {
    /**
     * {@inheritDoc}
     */
    public boolean verifyServerHostKey(final int handler,
                                       final String hostname,
                                       final int port,
                                       final String serverHostKeyAlgorithm,
                                       final String serverHostKey,
                                       final boolean isNew) {
      return getHandler(handler).verifyServerHostKey(hostname, port, serverHostKeyAlgorithm, serverHostKey, isNew);
    }

    /**
     * {@inheritDoc}
     */
    public String askPassphrase(final int handler, final String username, final String keyPath, final String lastError) {
      return adjustNull(getHandler(handler).askPassphrase(username, keyPath, lastError));
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    public Vector<String> replyToChallenge(final int handlerNo,
                                           final String username,
                                           final String name,
                                           final String instruction,
                                           final int numPrompts,
                                           final Vector<String> prompt,
                                           final Vector<Boolean> echo,
                                           final String lastError) {
      return adjustNull(getHandler(handlerNo).replyToChallenge(username, name, instruction, numPrompts, prompt, echo, lastError));
    }

    /**
     * {@inheritDoc}
     */
    public String askPassword(final int handlerNo, final String username, final String lastError) {
      return adjustNull(getHandler(handlerNo).askPassword(username, lastError));
    }

    /**
     * Adjust null value (by converting to {@link GitSSHService#XML_RPC_NULL_STRING})
     *
     * @param s a value to adjust
     * @return a string if non-nul or {@link GitSSHService#XML_RPC_NULL_STRING} if s == null
     */
    private String adjustNull(final String s) {
      return s == null ? XML_RPC_NULL_STRING : s;
    }

    /**
     * Adjust null value (returns empty array)
     *
     * @param s if null return empty array
     * @return s if not null, empty array otherwise
     */
    @SuppressWarnings({"UseOfObsoleteCollectionType"})
    private Vector adjustNull(final Vector s) {
      return s == null ? new Vector() : s;
    }
  }
}
