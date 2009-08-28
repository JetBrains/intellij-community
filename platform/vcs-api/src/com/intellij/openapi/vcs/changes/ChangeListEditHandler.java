package com.intellij.openapi.vcs.changes;

/**
 * for IDEA changelist name, IDEA changelist comment editing,
 * when, for example, under Perforce, comment corresponds to whole Perforce native changelist description,
 * while name is only a piece of description
 *
 * in that case, editing handler should be set for changelist in order to always edit name and comment consistently
 */
public interface ChangeListEditHandler {
  String changeCommentOnChangeName(final String name, final String comment);
  String changeNameOnChangeComment(final String name, final String comment);
  String correctCommentWhenInstalled(final String name, final String comment);
}
