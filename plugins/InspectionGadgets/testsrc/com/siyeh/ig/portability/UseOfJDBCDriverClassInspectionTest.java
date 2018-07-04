// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.portability;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UseOfJDBCDriverClassInspectionTest extends LightInspectionTestCase {

  public void testSimple() {
    doTest("import com.pany.*;" +
           "class X {" +
           "  /*Use of concrete JDBC driver class 'ConcreteDriver' is non-portable*/ConcreteDriver/**/ m() {" +
           "    return new /*Use of concrete JDBC driver class 'ConcreteDriver' is non-portable*/ConcreteDriver/**/();" +
           "  }" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UseOfJDBCDriverClassInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package com.pany;" +
      "import java.sql.*;" +
      "import java.util.Properties;" +
      "import java.util.logging.Logger;" +
      "public class ConcreteDriver implements Driver {" +
      "  @Overridepublic Connection connect(String url, Properties info) throws SQLException {" +
      "    return null;" +
      "  }" +
      "  @Overridepublic boolean acceptsURL(String url) throws SQLException {" +
      "    return false;" +
      "  }" +
      "  @Overridepublic DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {" +
      "    return new DriverPropertyInfo[0];" +
      "  }" +
      "  @Overridepublic int getMajorVersion() {" +
      "    return 0;" +
      "  }" +
      "  @Overridepublic int getMinorVersion() {" +
      "    return 0;" +
      "  }" +
      "  @Overridepublic boolean jdbcCompliant() {" +
      "    return false;" +
      "  }" +
      "  @Overridepublic Logger getParentLogger() throws SQLFeatureNotSupportedException {" +
      "    return null;" +
      "  }" +
      "}"
    };
  }
}