package com.intellij.driver.sdk.ui.components.clion

import com.intellij.util.system.OS

sealed class Toolchain(
  val name: ToolchainNames,
  val compiler: Compiler,
  val debugger: Debugger,
  val buildTool: BuildTool,
) {
  override fun toString(): String {
    return if (compiler == Compiler.DEFAULT) "${name}_$debugger"
    else "${name}_${compiler}_$debugger"
  }

  class Default(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.BUNDLED_GDB,
    buildTool: BuildTool = BuildTool.DEFAULT,
    name: ToolchainNames = ToolchainNames.DEFAULT,
  ) : Toolchain(name, compiler, debugger, buildTool)

  class Gnu(
    compiler: Compiler = Compiler.GCC,
    debugger: Debugger = Debugger.BUNDLED_GDB,
    buildTool: BuildTool = BuildTool.DEFAULT,
    name: ToolchainNames = ToolchainNames.GCC,
  ) : Toolchain(name, compiler, debugger, buildTool)

  class Llvm(
    compiler: Compiler = Compiler.CLANG,
    debugger: Debugger = Debugger.BUNDLED_LLDB,
    buildTool: BuildTool = BuildTool.DEFAULT,
    name: ToolchainNames = ToolchainNames.CLANG,
  ) : Toolchain(name, compiler, debugger, buildTool)

  class CustomGDB(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.CUSTOM_GDB,
    buildTool: BuildTool = BuildTool.DEFAULT,
    name: ToolchainNames = ToolchainNames.CUSTOM_GDB,
  ) : Toolchain(name, compiler, debugger, buildTool)

  class CustomLLDB(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.CUSTOM_LLDB,
    buildTool: BuildTool = BuildTool.DEFAULT,
    name: ToolchainNames = ToolchainNames.CUSTOM_LLDB,
  ) : Toolchain(name, compiler, debugger, buildTool)

  class MingwGDB(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.BUNDLED_GDB,
    buildTool: BuildTool = BuildTool.DEFAULT,
    name: ToolchainNames = ToolchainNames.MINGW_GDB,
  ) : Toolchain(name, compiler, debugger, buildTool)

  class CustomMingwGDB(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.CUSTOM_GDB,
    buildTool: BuildTool = BuildTool.DEFAULT,
    name: ToolchainNames = ToolchainNames.MINGW_GDB,
  ) : Toolchain(name, compiler, debugger, buildTool)

  class MSVC(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.BUNDLED_LLDB,
    buildTool: BuildTool = BuildTool.DEFAULT,
    name: ToolchainNames = ToolchainNames.MSVC,
  ) : Toolchain(name, compiler, debugger, buildTool)

  class Cygwin(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.CYGWIN_GDB,
    buildTool: BuildTool = BuildTool.DEFAULT,
    name: ToolchainNames = ToolchainNames.CYGWIN,
  ) : Toolchain(name, compiler, debugger, buildTool)

  class WSL(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.WSL_DEBUGGER,
    buildTool: BuildTool = BuildTool.GMAKE,
    name: ToolchainNames = ToolchainNames.WSL,
  ) : Toolchain(name, compiler, debugger, buildTool)

  class Docker(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.DOCKER_GDB,
    buildTool: BuildTool = BuildTool.GMAKE,
    name: ToolchainNames = ToolchainNames.DOCKER,
  ) : Toolchain(name, compiler, debugger, buildTool)

  class RemoteHost(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.REMOTE_GDB,
    buildTool: BuildTool = BuildTool.GMAKE,
    name: ToolchainNames = ToolchainNames.REMOTE_HOST,
  ) : Toolchain(name, compiler, debugger, buildTool)
}

enum class BuildTool {
  DEFAULT {
    override fun getPath(): String = ""
    override fun getName(): String = "ninja"
  },

  GMAKE {
    override fun getPath(): String = ""
    override fun getName(): String = "gmake"
  };

  abstract fun getPath(): String
  abstract fun getName(): String
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
    override fun getDebuggerFieldName(): String = "Bundled GDB"
    override fun toString(): String = "GDB"
    override fun type(): String = "GDB"
  },

  BUNDLED_LLDB {
    override fun getDebuggerPath(): String = "Bundled LLDB"
    override fun getDebuggerFieldName(): String = "Bundled LLDB"
    override fun toString(): String = "LLDB"
    override fun type(): String = "LLDB"
  },

  CUSTOM_GDB {
    override fun getDebuggerPath(): String = when (OS.CURRENT) {
      // Windows will use cygwin gdbserver executable as the custom gdb server
      OS.Windows -> "C:/Tools/cygwin/bin/gdbserver.exe"
      else -> "/usr/bin/gdb"
    }
    override fun getDebuggerFieldName(): String = "Custom GDB executable"
    override fun toString(): String = "Custom GDB"
    override fun type(): String = "GDB"
  },

  CUSTOM_LLDB {
    override fun getDebuggerPath(): String = "/usr/bin/lldb"
    override fun getDebuggerFieldName(): String = "Custom LLDB executable"
    override fun toString(): String = "Custom LLDB"
    override fun type(): String = "LLDB"
  },

  CYGWIN_GDB {
    override fun getDebuggerPath(): String = "Cygwin GDB"
    override fun getDebuggerFieldName(): String = "Custom GDB executable"
    override fun toString(): String = "Cygwin GDB"
    override fun type(): String = "GDB"
  },

  WSL_DEBUGGER {
    override fun getDebuggerPath(): String = "WSL GDB"
    override fun getDebuggerFieldName(): String = "Custom GDB executable"
    override fun toString(): String = "WSL GDB"
    override fun type(): String = "GDB"
  },

  DOCKER_GDB {
    override fun getDebuggerPath(): String = "Docker GDB"
    override fun getDebuggerFieldName(): String = "Custom GDB executable"
    override fun toString(): String = "Custom GDB executable"
    override fun type(): String = "GDB"
  },

  REMOTE_GDB {
    override fun getDebuggerPath(): String = "Remote Host GDB"
    override fun getDebuggerFieldName(): String = "Custom GDB executable"
    override fun toString(): String = "Remote Host GDB"
    override fun type(): String = "GDB"
  };

  abstract fun getDebuggerPath(): String
  abstract fun getDebuggerFieldName(): String
  abstract fun type(): String
}


enum class ToolchainNames {
  DEFAULT {
    override fun toString(): String = "Default"
  },
  GCC {
    override fun toString(): String = "GCC"
  },
  CLANG {
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
  },
  CUSTOM_GDB {
    override fun toString(): String = "Custom GDB executable"
  },
  CUSTOM_LLDB {
    override fun toString(): String = "Custom LLDB executable"
  },
  MINGW_GDB {
    override fun toString(): String = "MinGW"
  },
  MSVC {
    override fun toString(): String = "Visual Studio"
  },
  CYGWIN {
    override fun toString(): String = "Cygwin"
  },
  WSL {
    override fun toString(): String = "WSL"
  }
}

