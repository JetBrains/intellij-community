package com.intellij.ae.database.dbs

/**
 * A layer that uses SQLite to store data.
 *
 * Be very careful with these APIs! You MUST NOT make any write operations to the database
 */
interface ISqliteBackedDatabaseLayer {
  /**
   * Table name for the current layer. Use this in your SQL queries
   */
  val tableName: String
}