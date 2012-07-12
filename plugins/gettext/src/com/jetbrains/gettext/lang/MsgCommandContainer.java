package com.jetbrains.gettext.lang;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.gettext.GetTextTokenTypes;

import java.util.Map;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class MsgCommandContainer {

  private Map<IElementType, MsgCommand> commands;

  public MsgCommandContainer() {
    this.commands = new HashMap<IElementType, MsgCommand>();
    commands.put(GetTextTokenTypes.MSGID, new MsgidCommand());
    commands.put(GetTextTokenTypes.MSGSTR, new MsgstrCommand());
    commands.put(GetTextTokenTypes.MSGCTXT, new MsgctxtCommand());
    commands.put(GetTextTokenTypes.MSGID_PLURAL, new MsgidPluralCommand());
  }


  public boolean isFull() {
    for (MsgCommand command : commands.values()) {
      boolean full = !command.isNecessary() || command.exists();
      if (!full) {
        return false;
      }
    }
    return true;
  }

  private MsgCommand getCommand(IElementType token) throws UnknownCommandException {
    MsgCommand command = commands.get(token);
    if (command == null) {
      throw new UnknownCommandException("Unexpected token");
    }
    return command;
  }

  public boolean parse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    try {
      MsgCommand command = getCommand(builder.getTokenType());
      boolean result = command.parse(builder);
      if (result) {
        marker.done(command.getCompositeElement());
      }
      else {
        marker.error("String for " + command.getName() + " is not specified");
      }
      return result;
    }
    catch (UnknownCommandException e) {
      marker.error(e.getMessage());
      builder.advanceLexer();
      return false;
    }
  }

  public boolean addCommand(IElementType token) throws UnknownCommandException {
    return getCommand(token).register();
  }
}
