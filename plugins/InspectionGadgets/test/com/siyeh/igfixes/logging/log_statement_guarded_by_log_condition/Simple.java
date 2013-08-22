package com.siyeh.igfixes.logging.log_statement_guarded_by_log_condition;

import java.util.logging.Logger;

class Simple {

  private static final Logger LOG = Logger.getLogger("log");

  void m() {
    LOG.fine<caret>("asdfasd" + System.currentTimeMillis());
  }
}