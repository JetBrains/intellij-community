package com.intellij.driver.sdk.ui.components.clion

import com.intellij.util.system.OS
import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.inputStream


data class Toolset(
  val kind: String,
  val path: String? = null,
)

data class RemoteConnection(
  val host: String = "localhost",
  val port: String = "2222",
  val username: String = System.getenv("USERNAME") ?: "",
  val password: String = "wslpassword",
)

sealed class Toolchain(
  val name: ToolchainNames,
  val compiler: Compiler,
  val debugger: Debugger,
  val buildTool: BuildTool,
  val toolset: Toolset = Toolset(kind = "SYSTEM_UNIX_TOOLSET", path = null),
) {
  override fun toString(): String {
    return if (compiler == Compiler.DEFAULT) "${name}_$debugger"
    else "${name}_${compiler}_$debugger"
  }

  /**
   * Path of the GDB executable shipped with the MinGW toolset ("MinGW-w64 GDB" in the toolchain settings UI).
   *
   * It is better to specify not from the debugger Path to test that non bundled GDB is found, but this strategy only works from UI.
   * From XML setup, the full path must be specified.
   */
  val mingwToolsetGdbPath: String
    get() = "${toolset.path}/bin/gdb.exe"

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
    toolset: Toolset = Toolset(kind = "MINGW", path = "BUNDLED_MINGW"),
  ) : Toolchain(name, compiler, debugger, buildTool, toolset)

  class CustomMingw(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.MINGW_CUSTOM_GDB,
    buildTool: BuildTool = BuildTool.DEFAULT,
    name: ToolchainNames = ToolchainNames.MINGW_GDB,
    toolset: Toolset = Toolset(kind = "MINGW", path = WindowsToolchainPaths.mingw),
  ) : Toolchain(name, compiler, debugger, buildTool, toolset)

  class MingwCustomGDB(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.CUSTOM_GDB,
    buildTool: BuildTool = BuildTool.DEFAULT,
    name: ToolchainNames = ToolchainNames.MINGW_GDB,
    toolset: Toolset = Toolset(kind = "MINGW", path = WindowsToolchainPaths.mingw),
  ) : Toolchain(name, compiler, debugger, buildTool, toolset)

  class MSVC(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.BUNDLED_LLDB,
    buildTool: BuildTool = BuildTool.DEFAULT,
    name: ToolchainNames = ToolchainNames.MSVC,
    toolset: Toolset = Toolset(kind = "MSVC", path = WindowsToolchainPaths.msvc),
  ) : Toolchain(name, compiler, debugger, buildTool, toolset)

  class Cygwin(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.CYGWIN_GDB,
    buildTool: BuildTool = BuildTool.DEFAULT,
    name: ToolchainNames = ToolchainNames.CYGWIN,
    toolset: Toolset = Toolset(kind = "CYGWIN", path = WindowsToolchainPaths.cygwin),
  ) : Toolchain(name, compiler, debugger, buildTool, toolset)

  class WSL(
    compiler: Compiler = Compiler.DEFAULT,
    debugger: Debugger = Debugger.WSL_DEBUGGER,
    buildTool: BuildTool = BuildTool.GMAKE,
    name: ToolchainNames = ToolchainNames.WSL,
    toolset: Toolset = Toolset(kind = "WSL", path = DEFAULT_WSL_DISTRIBUTION),
  ) : Toolchain(name, compiler, debugger, buildTool, toolset)

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
    val connection: RemoteConnection = RemoteConnection(),
  ) : Toolchain(name, compiler, debugger, buildTool)

  companion object {
    const val DEFAULT_WSL_DISTRIBUTION: String = "ubuntu2204wsl2"
    /** The CMake executable from the Cygwin installation. */
    val CYGWIN_CMAKE_PATH: String
      get() = "${WindowsToolchainPaths.cygwin}/bin/cmake.exe"
  }
}

private object WindowsToolchainPaths {
  val mingw: String
    get() = installationPath("tools.msys2.mingw64")
  val msvc: String
    get() = installationPath("tools.msvs.2026.installDir")
  val cygwin: String
    get() = installationPath("tools.cygwin.installDir")

  private val configurationParameters: Properties by lazy {
    val configurationFile = System.getProperty("teamcity.configuration.properties.file")
                            ?: System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE")?.let {
                              loadProperties(it).getProperty("teamcity.configuration.properties.file")
                            }
    configurationFile?.let { loadProperties(it) } ?: Properties()
  }

  private fun installationPath(parameterName: String): String {
    val path = System.getProperty(parameterName)?.takeIf { it.isNotBlank() }
               ?: configurationParameters.getProperty(parameterName)?.takeIf { it.isNotBlank() }
    return checkNotNull(path) {
      "Missing toolchain parameter '$parameterName'. For a local run, set -D$parameterName=<installation directory>."
    }.replace('\\', '/').trimEnd('/')
  }

  private fun loadProperties(file: String): Properties = Properties().apply {
    Path(file).inputStream().use { load(it) }
  }
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

  MINGW_CUSTOM_GDB {
    override fun getDebuggerPath(): String = "MinGW-w64 GDB"
    override fun getDebuggerFieldName(): String = "MinGW-w64 GDB"
    override fun toString(): String = "MinGW-w64 GDB"
    override fun type(): String = "GDB"
  },

  CUSTOM_GDB {
    override fun getDebuggerPath(): String = when (OS.CURRENT) {
      // Windows will use cygwin gdbserver executable as the custom gdb server
      OS.Windows -> "${WindowsToolchainPaths.cygwin}/bin/gdbserver.exe"
      else -> "/usr/bin/gdb"
    }

    override fun getDebuggerFieldName(): String = "Custom GDB executable"
    override fun toString(): String = "Custom GDB"
    override fun type(): String = "GDB"
    override val shouldTypePath: Boolean = true
  },

  CUSTOM_LLDB {
    override fun getDebuggerPath(): String = "/usr/bin/lldb"
    override fun getDebuggerFieldName(): String = "Custom LLDB executable"
    override fun toString(): String = "Custom LLDB"
    override fun type(): String = "LLDB"
    override val shouldTypePath: Boolean = true
  },

  CYGWIN_GDB {
    override fun getDebuggerPath(): String = "${WindowsToolchainPaths.cygwin}/bin/gdbserver.exe"
    override fun getDebuggerFieldName(): String = "Custom GDB executable"
    override fun toString(): String = "Custom Cygwin GDB"
    override fun type(): String = "GDB"
    override val shouldTypePath: Boolean = true
  },

  WSL_DEBUGGER {
    override fun getDebuggerPath(): String = "/usr/bin/gdb"
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

  /** Whether [ToolchainPanel.setDebugger] must type the debugger path into the field after selecting it from the popup. */
  open val shouldTypePath: Boolean = false
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

