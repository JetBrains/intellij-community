package com.siyeh.igfixes.logging.log_statement_guarded_by_log_condition;

import java.util.logging.Logger;

class Simple {

  private static final Logger LOG = Logger.getLogger("log");

  void m() {
      if (LOG.isLoggable(java.util.logging.Level.FINE)) {
          LOG.fine("asdfasd" + System.currentTimeMillis());
      }
  }
}