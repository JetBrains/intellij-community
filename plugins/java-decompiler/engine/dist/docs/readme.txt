1. About the decompiler 

Fernflower is the first actually working analytical decompiler for Java and 
probably for a high-level programming language in general. Naturally it is still 
under development, please send your bug reports and improvement suggestions at 
fernflower.decompiler@gmail.com


2. License

See license_en.txt 


3. Running from the command line

java -jar fernflower.jar [-<option>=<value>]* [<source>]+ <destination>

* means 0 or more times
+ means 1 or more times

<source>: file or directory with files to be decompiled. Directories are recursively scanned. Allowed file extensions are class, zip and jar.
          Sources prefixed with -e= mean "library" files that won't be decompiled, but taken into account when analysing relationships between 
          classes or methods. Especially renaming of identifiers (s. option 'ren') can benefit from information about external classes.          
<destination>: destination directory 
<option>,<value>: command line option with the corresponding value, see 4.

Examples:

java -jar fernflower.jar -hes=0 -hdc=0 c:\Temp\binary\ -e=c:\Java\rt.jar c:\Temp\source\

java -jar fernflower.jar -dgs=1 c:\Temp\binary\library.jar c:\Temp\binary\Boot.class c:\Temp\source\


4. Command line options

With the exception of mpm and urc the value of 1 means the option is activated, 0 - deactivated. Default 
value, if any, is given between parentheses.

Typically, the following options will be changed by user, if any: hes, hdc, dgs, mpm, ren, urc 
The rest of options can be left as they are: they are aimed at professional reverse engineers.

rbr (1): hide bridge methods
rsy (0): hide synthetic class members
din (1): decompile inner classes
dc4 (1): collapse 1.4 class references
das (1): decompile assertions
hes (1): hide empty super invocation
hdc (1): hide empty default constructor
dgs (0): decompile generic signatures
occ (0): ouput copyright comment
ner (1): assume return not throwing exceptions
den (1): decompile enumerations
rgn (1): remove getClass() invocation, when it is part of a qualified new statement
bto (1): interpret int 1 as boolean true (workaround to a compiler bug)
nns (1): allow for not set synthetic attribute (workaround to a compiler bug)
uto (1): consider nameless types as java.lang.Object (workaround to a compiler architecture flaw)
udv (1): reconstruct variable names from debug information, if present
rer (1): remove empty exception ranges
fdi (1): deinline finally structures
asc (0): allow only ASCII characters in string literals. All other characters will be encoded using Unicode escapes (JLS 3.3). Default encoding is UTF8.  
mpm (0): maximum allowed processing time per decompiled method, in seconds. 0 means no upper limit.
ren (0): rename ambiguous (resp. obfuscated) classes and class elements
urc    : full name of user-supplied class implementing IIdentifierRenamer. It is used to determine which
         class identifiers should be renamed and provides new identifier names. For more information 
         s. section 5    
 
The default logging level is INFO. This value can be overwritten by setting the option 'log' as follows:

log (INFO): possible values TRACE, INFO, WARN, ERROR  


5. Renaming identifiers

Some obfuscators give classes and their member elements short, meaningless and above all ambiguous names. Recompiling of such
code leads to a great number of conflicts. Therefore it is advisable to let the decompiler rename elements in its turn, 
ensuring uniqueness of each identifier.

Option 'ren' (i.e. -ren=1) activates renaming functionality. Default renaming strategy goes as follows:
- rename an element if its name is a reserved word or is shorter than 3 characters
- new names are built according to a simple pattern: (class|method|field)_<consecutive unique number>  
You can overwrite this rules by providing your own implementation of the 4 key methods invoked by the decompiler while renaming. Simply 
pass a class that implements de.fernflower.main.extern.IIdentifierRenamer in the option 'urc' (e.g. -urc=com.mypackage.MyRenamer) to 
Fernflower. The class must be available on the application classpath.

The meaning of each method should be clear from naming: toBeRenamed determine whether the element will be renamed, while the other three
provide new names for classes, methods and fields respectively.  
