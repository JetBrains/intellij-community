package com.intellij.ae.database.dbs.migrations

import org.intellij.lang.annotations.Language

@Language("SQLite")
internal val MIGRATION_V2 = """
ALTER TABLE "timespanUserActivity" ADD COLUMN is_finished INTEGER DEFAULT 0;
"""
