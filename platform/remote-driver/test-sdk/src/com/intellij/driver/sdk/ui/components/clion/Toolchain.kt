package com.intellij.driver.sdk.ui.components.clion

sealed class Toolchain(val name: ToolchainNames)

class DefaultToolchain(
  val compiler: Compiler = Compiler.DEFAULT,
  val debugger: Debugger = Debugger.BUNDLED_GDB,
  val buildTool: Make = Make.DEFAULT,
  name: ToolchainNames = ToolchainNames.DEFAULT,
) : Toolchain(name) {
  override fun toString(): String = "${compiler}_$debugger"
}

class GCCToolchain(
  val compiler: Compiler = Compiler.GCC,
  val debugger: Debugger = Debugger.BUNDLED_GDB,
  val buildTool: Make = Make.DEFAULT,
  name: ToolchainNames = ToolchainNames.GCC,
) : Toolchain(name) {
  override fun toString(): String = "${compiler}_$debugger"
}

class CLangToolchain(
  val compiler: Compiler = Compiler.CLANG,
  val debugger: Debugger = Debugger.BUNDLED_LLDB,
  val buildTool: Make = Make.DEFAULT,
  name: ToolchainNames = ToolchainNames.CLang,
) : Toolchain(name) {
  override fun toString(): String = "${compiler}_$debugger"
}

@Suppress("unused")
enum class Make {
  DEFAULT {
    override fun getMakePath(): String = ""
  };

  abstract fun getMakePath(): String
}

enum class Compiler {
  DEFAULT {
    override fun getCppCompilerPath(): String = ""
    override fun getCCompilerPath(): String = ""
  },

  GCC {
    override fun getCppCompilerPath(): String = System.getenv("TOOLCHAINS_CPP_GNU") ?: ""
    override fun getCCompilerPath(): String = System.getenv("TOOLCHAINS_C_GNU") ?: ""
  },

  CLANG {
    override fun getCppCompilerPath(): String = System.getenv("TOOLCHAINS_CPP_CLANG") ?: ""
    override fun getCCompilerPath(): String = System.getenv("TOOLCHAINS_C_CLANG") ?: ""
  };

  abstract fun getCppCompilerPath(): String
  abstract fun getCCompilerPath(): String
}

enum class Debugger {
  BUNDLED_GDB {
    override fun getDebuggerPath(): String = "Bundled GDB"
  },

  BUNDLED_LLDB {
    override fun getDebuggerPath(): String = "Bundled LLDB"
  };

  abstract fun getDebuggerPath(): String
}


enum class ToolchainNames {
  DEFAULT {
    override fun toString(): String = "Default"
  },
  GCC {
    override fun toString(): String = "GCC"
  },
  CLang {
    override fun toString(): String = "CLang"
  },
  SYSTEM {
    override fun toString(): String = "System"
  },
  REMOTE_HOST {
    override fun toString(): String = "Remote Host"
  },
  DOCKER {
    override fun toString(): String = "Docker"
  }
}

