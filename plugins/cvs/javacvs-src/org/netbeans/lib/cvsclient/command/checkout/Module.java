package org.netbeans.lib.cvsclient.command.checkout;

/**
 * @author  Thomas Singer
 */
public class Module {

	// Fields =================================================================

	private final String moduleName;
	private String options;

	// Setup ==================================================================

	public Module(String moduleName) {
		this(moduleName, null);
	}

	public Module(String moduleName, String alias) {
		this.moduleName = moduleName;
		this.options = alias;
	}

	// Implemented ============================================================

	public int hashCode() {
		return moduleName.hashCode();
	}

	public boolean equals(Object obj) {
		return ((Module)obj).moduleName.equals(moduleName);
	}

	public String toString() {
		return moduleName;
	}

	// Accessing ==============================================================

	public String getModuleName() {
		return moduleName;
	}

	public String getOptions() {
		return options;
	}

  public void appendOptions(String s) {
    options = options + " " + s;
  }
}
