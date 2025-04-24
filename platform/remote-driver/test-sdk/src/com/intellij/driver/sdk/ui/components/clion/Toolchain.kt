package com.intellij.driver.sdk.ui.components.clion

sealed class Toolchain(
  val name: ToolchainNames,
  val compiler: Compiler,
  val debugger: Debugger,
  val buildTool: Make
) {
  override fun toString(): String = "${compiler}_$debugger"

  class Default(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.BUNDLED_GDB,
    buildTool: Make = Make.DEFAULT,
    name: ToolchainNames = ToolchainNames.DEFAULT
  ) : Toolchain(name, compiler, debugger, buildTool)

  class Gnu(
    compiler: Compiler = Compiler.GCC,
    debugger: Debugger = Debugger.BUNDLED_GDB,
    buildTool: Make = Make.DEFAULT,
    name: ToolchainNames = ToolchainNames.GCC
  ) : Toolchain(name, compiler, debugger, buildTool)

  class Llvm(
    compiler: Compiler = Compiler.CLANG,
    debugger: Debugger = Debugger.BUNDLED_LLDB,
    buildTool: Make = Make.DEFAULT,
    name: ToolchainNames = ToolchainNames.CLang
  ) : Toolchain(name, compiler, debugger, buildTool)
}

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

