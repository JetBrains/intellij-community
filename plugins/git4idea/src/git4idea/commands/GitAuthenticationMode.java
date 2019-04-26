// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

/**
 * Flag to get user credentials in different interaction modes:
 * NONE: no authentication will be performed if password is requested
 * (Also, native credential helper should be disabled manually as a command parameter);
 * SILENT: the IDE will look for passwords in the common password storages. if no password is found, no authentication will be performed;
 * FULL: the IDE will look for passwords in the common password storages. If no password is found, an authentication dialog will be displayed.
 */
public enum GitAuthenticationMode {
  NONE, SILENT, FULL
}
