/*
 * Copyright (c) 2007 David Crawshaw <david@zentus.com>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package org.jetbrains.sqlite;

import org.jetbrains.sqlite.core.Codes;
import org.jetbrains.sqlite.core.DB;
import org.jetbrains.sqlite.core.SqliteConnection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides an interface for creating SQLite user-defined functions.
 *
 * <p>A subclass of <tt>org.sqlite.Function</tt> can be registered with <tt>Function.create()</tt>
 * and called by the name it was given. All functions must implement <tt>xFunc()</tt>, which is
 * called when SQLite runs the custom function. E.g.
 *
 * <pre>
 *      Class.forName("org.sqlite.JDBC");
 *      Connection conn = DriverManager.getConnection("jdbc:sqlite:");
 *
 *      Function.create(conn, "myFunc", new Function() {
 *          protected void xFunc() {
 *              System.out.println("myFunc called!");
 *          }
 *      });
 *
 *      conn.createStatement().execute("select myFunc();");
 *  </pre>
 *
 * <p>Arguments passed to a custom function can be accessed using the <tt>protected</tt> functions
 * provided. <tt>args()</tt> returns the number of arguments passed, while
 * <tt>value_&lt;type&gt;(int)</tt> returns the value of the specific argument. Similarly, a
 * function can return a value using the <tt>result(&lt;type&gt;)</tt> function.
 */
public abstract class Function {
  /**
   * Flag to provide to {@link #create(Connection, String, Function, int)} that marks this
   * Function as deterministic, making is usable in Indexes on Expressions.
   */
  public static final int FLAG_DETERMINISTIC = 0x800;
  long context = 0; // pointer sqlite3_context*
  long value = 0; // pointer sqlite3_value**
  int args = 0;
  private SqliteConnection conn;
  private DB db;

  /**
   * Called by SQLite as a custom function. Should access arguments through <tt>value_*(int)</tt>,
   * return results with <tt>result(*)</tt> and throw errors with <tt>error(String)</tt>.
   */
  protected abstract void xFunc();

  /**
   * Returns the number of arguments passed to the function. Can only be called from
   * <tt>xFunc()</tt>.
   */
  protected final synchronized int args() throws SQLException {
    checkContext();
    return args;
  }

  /**
   * Called by <tt>xFunc</tt> to return a value.
   *
   */
  protected final synchronized void result(byte[] value) throws SQLException {
    checkContext();
    db.result_blob(context, value);
  }

  /**
   * Called by <tt>xFunc</tt> to return a value.
   *
   */
  protected final synchronized void result(double value) throws SQLException {
    checkContext();
    db.result_double(context, value);
  }

  /**
   * Called by <tt>xFunc</tt> to return a value.
   *
   */
  protected final synchronized void result(int value) throws SQLException {
    checkContext();
    db.result_int(context, value);
  }

  /**
   * Called by <tt>xFunc</tt> to return a value.
   *
   */
  protected final synchronized void result(long value) throws SQLException {
    checkContext();
    db.result_long(context, value);
  }

  /** Called by <tt>xFunc</tt> to return a value. */
  protected final synchronized void result() throws SQLException {
    checkContext();
    db.result_null(context);
  }

  /**
   * Called by <tt>xFunc</tt> to return a value.
   *
   */
  protected final synchronized void result(String value) throws SQLException {
    checkContext();
    db.result_text(context, value);
  }

  /**
   * Called by <tt>xFunc</tt> to throw an error.
   *
   */
  protected final synchronized void error(String err) throws SQLException {
    checkContext();
    db.result_error(context, err);
  }

  /**
   * Called by <tt>xFunc</tt> to access the value of an argument.
   *
   */
  protected final synchronized String value_text(int arg) throws SQLException {
    checkValue(arg);
    return db.value_text(this, arg);
  }

  /**
   * Called by <tt>xFunc</tt> to access the value of an argument.
   *
   */
  protected final synchronized byte[] value_blob(int arg) throws SQLException {
    checkValue(arg);
    return db.value_blob(this, arg);
  }

  /**
   * Called by <tt>xFunc</tt> to access the value of an argument.
   *
   */
  protected final synchronized double value_double(int arg) throws SQLException {
    checkValue(arg);
    return db.value_double(this, arg);
  }

  /**
   * Called by <tt>xFunc</tt> to access the value of an argument.
   *
   */
  protected final synchronized int value_int(int arg) throws SQLException {
    checkValue(arg);
    return db.value_int(this, arg);
  }

  /**
   * Called by <tt>xFunc</tt> to access the value of an argument.
   *
   */
  protected final synchronized long value_long(int arg) throws SQLException {
    checkValue(arg);
    return db.value_long(this, arg);
  }

  /**
   * Called by <tt>xFunc</tt> to access the value of an argument.
   *
   */
  protected final synchronized int value_type(int arg) throws SQLException {
    checkValue(arg);
    return db.value_type(this, arg);
  }

  /** */
  private void checkContext() throws SQLException {
    if (conn == null || conn.getDatabase() == null || context == 0) {
      throw new SQLException("no context, not allowed to read value");
    }
  }

  /**
   */
  private void checkValue(int arg) throws SQLException {
    if (conn == null || conn.getDatabase() == null || value == 0) {
      throw new SQLException("not in value access state");
    }
    if (arg >= args) {
      throw new SQLException("arg " + arg + " out bounds [0," + args + ")");
    }
  }

  /**
   * Registers a given function with the connection.
   *
   * @param conn The connection.
   * @param name The name of the function.
   * @param f    The function to register.
   */
  public static void create(SqliteConnection conn, String name, Function f) throws SQLException {
    create(conn, name, f, 0);
  }

  /**
   * Registers a given function with the connection.
   *
   * @param conn  The connection.
   * @param name  The name of the function.
   * @param f     The function to register.
   * @param flags Extra flags to pass, such as {@link #FLAG_DETERMINISTIC}
   */
  public static void create(SqliteConnection conn, String name, Function f, int flags)
    throws SQLException {
    create(conn, name, f, -1, flags);
  }

  /**
   * Registers a given function with the connection.
   *
   * @param conn  The connection.
   * @param name  The name of the function.
   * @param f     The function to register.
   * @param nArgs The number of arguments that the function takes.
   * @param flags Extra flags to pass, such as {@link #FLAG_DETERMINISTIC}
   */
  public static void create(SqliteConnection conn, String name, Function f, int nArgs, int flags)
    throws SQLException {
    if (conn.isClosed()) {
      throw new SQLException("connection closed");
    }

    f.conn = conn;
    f.db = f.conn.getDatabase();

    if (nArgs < -1 || nArgs > 127) {
      throw new SQLException("invalid args provided: " + nArgs);
    }

    if (f.db.create_function(name, f, nArgs, flags) != Codes.SQLITE_OK) {
      throw new SQLException("error creating function");
    }
  }

  /**
   * Removes a named function from the given connection.
   *
   * @param conn  The connection to remove the function from.
   * @param name  The name of the function.
   * @param nArgs Ignored.
   */
  public static void destroy(SqliteConnection conn, String name, int nArgs) throws SQLException {
    conn.getDatabase().destroy_function(name);
  }

  /**
   * Removes a named function from the given connection.
   *
   * @param conn The connection to remove the function from.
   * @param name The name of the function.
   */
  public static void destroy(SqliteConnection conn, String name) throws SQLException {
    destroy(conn, name, -1);
  }

  /**
   * Provides an interface for creating SQLite user-defined aggregate functions.
   *
   * @see Function
   */
  public abstract static class Aggregate extends Function implements Cloneable {
    /** @see Function#xFunc() */
    @Override
    protected final void xFunc() { }

    /**
     * Defines the abstract aggregate callback function
     *
     * @see <a
     * href="http://www.sqlite.org/c3ref/aggregate_context.html">http://www.sqlite.org/c3ref/aggregate_context.html</a>
     */
    protected abstract void xStep();

    /**
     * Defines the abstract aggregate callback function
     *
     * @see <a
     * href="http://www.sqlite.org/c3ref/aggregate_context.html">http://www.sqlite.org/c3ref/aggregate_context.html</a>
     */
    protected abstract void xFinal();

    /** @see Object#clone() */
    @Override
    public Object clone() throws CloneNotSupportedException {
      return super.clone();
    }
  }

  /**
   * Provides an interface for creating SQLite user-defined window functions.
   *
   * @see Aggregate
   */
  public abstract static class Window extends Aggregate {
    /**
     * Defines the abstract window callback function
     *
     * @see <a
     * href="https://sqlite.org/windowfunctions.html#user_defined_aggregate_window_functions">https://sqlite.org/windowfunctions.html#user_defined_aggregate_window_functions</a>
     */
    protected abstract void xInverse();

    /**
     * Defines the abstract window callback function
     *
     * @see <a
     * href="https://sqlite.org/windowfunctions.html#user_defined_aggregate_window_functions">https://sqlite.org/windowfunctions.html#user_defined_aggregate_window_functions</a>
     */
    protected abstract void xValue();
  }
}
