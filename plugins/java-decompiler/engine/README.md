### About Fernflower

Fernflower is the first actually working analytical decompiler for Java and 
probably for a high-level programming language in general. Naturally it is still 
under development, please send your bug reports and improvement suggestions at 
fernflower.decompiler@gmail.com

### Licence

Fernflower is licenced under the [Apache Licence Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

### Running from command line

`java -jar fernflower.jar [-<option>=<value>]* [<source>]+ <destination>`

\* means 0 or more times\
\+ means 1 or more times

\<source>: file or directory with files to be decompiled. Directories are recursively scanned. Allowed file extensions are class, zip and jar.
          Sources prefixed with -e= mean "library" files that won't be decompiled, but taken into account when analysing relationships between 
          classes or methods. Especially renaming of identifiers (s. option 'ren') can benefit from information about external classes.          

\<destination>: destination directory 

\<option>, \<value>: a command-line option with the corresponding value (see "Command-line options" below).

##### Examples:

`java -jar fernflower.jar -hes=0 -hdc=0 c:\Temp\binary\ -e=c:\Java\rt.jar c:\Temp\source\`

`java -jar fernflower.jar -dgs=1 c:\Temp\binary\library.jar c:\Temp\binary\Boot.class c:\Temp\source\`

### Command-line options

With the exception of mpm and urc the value of 1 means the option is activated, 0 - deactivated. Default 
value, if any, is given between parentheses.

Typically, the following options will be changed by user, if any: hes, hdc, dgs, mpm, ren, urc 
The rest of options can be left as they are: they are aimed at professional reverse engineers.

- rbr (1): hide bridge methods
- rsy (0): hide synthetic class members
- din (1): decompile inner classes
- dc4 (1): collapse 1.4 class references
- das (1): decompile assertions
- hes (1): hide empty super invocation
- hdc (1): hide empty default constructor
- dgs (0): decompile generic signatures
- ner (1): assume return not throwing exceptions
- den (1): decompile enumerations
- rgn (1): remove getClass() invocation, when it is part of a qualified new statement
- lit (0): output numeric literals "as-is"
- asc (0): encode non-ASCII characters in string and character literals as Unicode escapes
- bto (1): interpret int 1 as boolean true (workaround to a compiler bug)
- nns (0): allow for not set synthetic attribute (workaround to a compiler bug)
- uto (1): consider nameless types as java.lang.Object (workaround to a compiler architecture flaw)
- udv (1): reconstruct variable names from debug information, if present
- rer (1): remove empty exception ranges
- fdi (1): de-inline finally structures
- mpm (0): maximum allowed processing time per decompiled method, in seconds. 0 means no upper limit
- ren (0): rename ambiguous (resp. obfuscated) classes and class elements
- urc (-): full name of a user-supplied class implementing IIdentifierRenamer interface. It is used to determine which class identifiers
           should be renamed and provides new identifier names (see "Renaming identifiers")
- inn (1): check for IntelliJ IDEA-specific @NotNull annotation and remove inserted code if found
- lac (0): decompile lambda expressions to anonymous classes
- nls (0): define new line character to be used for output. 0 - '\r\n' (Windows), 1 - '\n' (Unix), default is OS-dependent
- ind: indentation string (default is 3 spaces)
- log (INFO): a logging level, possible values are TRACE, INFO, WARN, ERROR

### Renaming identifiers

Some obfuscators give classes and their member elements short, meaningless and above all ambiguous names. Recompiling of such
code leads to a great number of conflicts. Therefore it is advisable to let the decompiler rename elements in its turn, 
ensuring uniqueness of each identifier.

Option 'ren' (i.e. -ren=1) activates renaming functionality. Default renaming strategy goes as follows:
- rename an element if its name is a reserved word or is shorter than 3 characters
- new names are built according to a simple pattern: (class|method|field)_\<consecutive unique number>  
You can overwrite this rules by providing your own implementation of the 4 key methods invoked by the decompiler while renaming. Simply 
pass a class that implements org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer in the option 'urc'
(e.g. -urc=com.example.MyRenamer) to Fernflower. The class must be available on the application classpath.

The meaning of each method should be clear from naming: toBeRenamed determine whether the element will be renamed, while the other three
provide new names for classes, methods and fields respectively.  
