package org.jetbrains.plugins.groovy.mvc;

import com.intellij.execution.configurations.ParametersList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class MvcCommand {

  public static final Collection<String> ourEnvironments = Arrays.asList("prod", "test", "dev");

  private String myEnv;
  private String myCommand;

  private final ArrayList<String> myArgs = new ArrayList<String>();
  private final ArrayList<String> myProperties = new ArrayList<String>();

  public MvcCommand() {
  }

  public MvcCommand(String command, String ... args) {
    myCommand = command;
    Collections.addAll(myArgs, args);
  }

  @Nullable
  public String getEnv() {
    return myEnv;
  }

  public void setEnv(@Nullable String env) {
    myEnv = env;
  }

  @Nullable
  public String getCommand() {
    return myCommand;
  }

  public void setCommand(@Nullable String command) {
    myCommand = command;
  }

  /**
   * Returns MODIFIABLE list of arguments
   */
  public ArrayList<String> getArgs() {
    return myArgs;
  }

  public void setArgs(List<String> args) {
    if (args == myArgs) return;

    myArgs.clear();
    myArgs.addAll(args);
  }

  /**
   * Returns MODIFIABLE list of system properties definition written before command (e.g. -Dgrails.port=9090 run-app)
   */
  public ArrayList<String> getProperties() {
    return myProperties;
  }

  public void setProperties(List<String> properties) {
    if (myProperties == properties) return;

    myProperties.clear();
    myProperties.addAll(properties);
  }

  public void addToParametersList(@NotNull ParametersList list) {
    if (myEnv != null) {
      list.add(myEnv);
    }

    list.addAll(myProperties);

    if (myCommand != null) {
      list.add(myCommand);
    }

    list.addAll(myArgs);
  }

  @NotNull
  public static MvcCommand parse(@NotNull String cmd) {
    String[] args = ParametersList.parse(cmd);

    MvcCommand res = new MvcCommand();

    int i = 0;

    while (res.myCommand == null && i < args.length) {
      String s = args[i];

      if (s.startsWith("-D")) {
        res.myProperties.add(s);
      }
      else if (res.myEnv == null && ourEnvironments.contains(s)) {
        res.myEnv = s;
      }
      else {
        res.myCommand = s;
      }

      i++;
    }

    res.myArgs.addAll(Arrays.asList(args).subList(i, args.length));

    return res;
  }
}
