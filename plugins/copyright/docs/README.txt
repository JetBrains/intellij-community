@NAME@ version @VERSION@ Release Notes for IDEA @IDEA-VERSION@

NOTE: This version requires IDEA to be executed with JDK 1.5.0 or greater.

Project/Module Copyright Notice.

This plugin is used to ensure files in a project or module have a consistent
copyright notice. Copyright notices can be created for the following file
types: Java, JavaScript, JSP, JSPx, XML, HTML, xHTML, DTD, Properties, and CSS.

* Configuration

** Enable Plugin

By default the plugin is disabled for a project. Simple select the Enable
Copyright Notice Plugin checkbox to enable copyright notices for this project.

** Project and Module Level

You can set the copyright notice at the project level first. This setting
will be used as the default for each new module added to the project. You
then have the option of adjusting each module's setting individually. For each
module you can specify whether it has it's own custom copyright, uses the
project copyright, or has no copyright at all.

You can also import copyright notice settings from other, external projects and
modules by pressing the Import Settings button. This brings up a file chooser
that lets you select project (*.ipr) or module (*.iml) files. Once selected the
project or module is scanned for copyright plugin settings. If found they are
imported into the current level.

At the module level you can also copy over the current project's settings by
pressing the Copy Project Settings button.

** Settings

At a given level (project or module) you can define a copyright notice template
to be used by all supported file types and/or you can tailor the copyright
notice settings for a specific file type.

*** Template

**** Text

Most likely you will want the same general copyright notice for all file types.
This is done by setting up the template. The copyright notice template should
be undecorated and language independent. In other words, the template should
not contain any language specific comment markers.

The template will be treated as a Velocity template. See the section below for
more information on Velocity templates.

**** Formatting Options

Once the basic text of the copyright notice is set you can specify several
formatting options.

First you should specify whether the copyright should be entered using a
file's block or line comment syntax. A language not containing the selected
choice will use the syntax it does have available to best emulate the choice. If
the block comment is chosen you have the option of having each line preceded by
a comment marker.

Second you can specify whether the comment should be surrounded by a line of
comment markers, referred to as a separator. If both separators are selected
and are of the same length you will have the option of having the whole comment
"boxed" in. The character used to fill the separator can specified. By default
(nothing specified) the character used will be appropriate for the given
language. Tilde (~) is used for XML type languages because a hypen (-) isn't
valid when strung together.

**** Relative Location

If the area containing the copyright notice contains other, non-copyright
comments you can specify whether the copyright notice comes before or after the
other comments.

You may also specify that you want a blank line to appear after the copyright
notice.

**** Copyright Keyword

This keyword must be a valid Java regular expression (see
java.util.regex.Pattern) used to determine if a comment is a normal comment or a
copyright notice comment. It is important that the keyword expression doesn't
match non-copyright notice comments or you may find comments getting deleted
that you didn't want deleted. This warning is only true for comments near the
beginning of files within the areas that copyright notices may appear. Comments
in other parts of a file will not be effected even if the keyword expression
matches.

**** Preview

Checking the Preview checkbox allows you to see what your comment will look like
using Java comment syntax. This is your entered text formatted with all the
options chosen in the Formatting Options area.

**** Validate

Pressing this button verifies that the text is a valid Velocity template. If
the Velocity engine finds any problem with the text an error message is
displayed. This does not verify whether the resulting comment is valid for the
given file type.

*** File Types

By default all supported file types will use the template copyright notice.
You do have the option of changing the settings for a specific file type.

**** Template Override

No Copyright - Select this if you don't want any copyright notice added to
   files of this type. If a copyright notice exists it will be removed.
Use Template - Use the template copyright notice.
Use Template Text - Select this if you want to use the text of the template
   copyright notice but you wish to format the text differently.
Fully Custom - Select this if you need to enter a copyright notice in a form
   not supported by the supplied formatting options. If you choose this
   option then the text you enter must be a complete and valid comment for
   the given file type. See the section below on multi-comment notices.

**** Formatting Options

This is only enabled if Use Template Text has been selected. The options work
just as described under the Template section. The only exception is that the
Block and Line comment choices are enabled based on the features of the
language.

**** Alternate Comment Syntax

JSP files can use either JSP comments or XML comments. By default the JSP
syntax is used. To use XML you must check the Use XML Comments checkbox.

**** Relative Location

This is enabled if Use Template Text or Fully Custom has been selected. The
options work just as described under the Template section.

**** File Location

This specifies where within a file the copyright notice is to be placed.

***** Java

The copyright notice can be placed in one of the following locations:

- At the start of the file before the "package" statement.
- Between the package statement and any imports.
- Between the imports and the first top level class (before the class' Javadoc
  comment, if present).

***** XML/DTD/HTML/xHTML/JSP/JSPx

The copyright notice can be placed in one of the following locations:

- Before the <!DOCTYPE> specification.
- Before the first tag of the document.

***** All Other File Types

For all other file types the copyright notice must appear in the begining of
the file before any non-comment, non-whitespace content.

* Multi-Comment Notices and Blank Lines

A copyright notice may contain multiple comments. This is a given when using
line comments and the copyright notice contains more than one line of text. You
can also have muliple block comments in a single copyright notice. This can only
happen when using a Fully Custom copyright notice. A Fully Custom copyright
notice also allows for any combination of line and block comments to be used.

There is one very important consideration though when your copyright notice
contains multiple comments - there can't be any blank lines between any of the
comments. Note - this doesn't mean there can't be blank lines in the copyright
notice. They just need to be within a block comment.

If you have a Fully Custom copyright notice with multiple comments and you
place blank lines between any of them, you will most likely get undesirable
effects when you perform copyright updates on files of that type.

* Menus

The option to update the copyright notice will appear in the following
locations:

- At the end of the context menu for a supported file in the project tree.
- At the end of the context menu for a supported file in the packages tree.
- At the end of the context menu for a supported file in an editor.
- At the end of the context menu for a directory in the project tree.
- At the end of the context menu for a package in the packages tree.
- At the end of the context menu for a module in the packages tree.
- At the end of the context menu for a module in the project tree.
- At the end of the Code menu if the current editor contains a supported file.
- At the end of the Generate popup menu if the current editor contains a
  supported file.

Selecting a file or directory in the project tree works just like Optimize
Imports. You will have the option of updating a single file or the whole
directory.

New files will automatically have the correct copyright notice applied to them
if the file is a supported type and the file type has copyright notices enabled.

* Copyright Template

The contents of the copyright notice can be plain text or a Velocity template.
See http://jakarta.apache.org/velocity for more information on Velocity and see
http://jakarta.apache.org/velocity/user-guide.html for more details on writing
Velocity templates.

Currently the following variables are available in the Velocity context:

Name                     Type     Comment
======================== ======== ============================================
$today                   DateInfo Represents the current date and time
$file.fileName           String   The current file's name
$file.pathName           String   The current file's complete path and name
$file.className          String   The current java file's classname
$file.qualifiedClassName String   The current java file's fully qualified name
$file.lastModified       DateInfo The date and time the file was last changed
$project.name            String   The current project's name
$module.name             String   The current module's name
$username                String   The user's name

DateInfo has the following properties:

Name                     Type     Comment
======================== ======== ============================================
year                     int      The date's year
month                    int      The date's month (1 - 12)
day                      int      The date's day of month (1 - 31)
hour                     int      The date's hour (0 - 23)
minute                   int      The date's minute of the hour (0 - 59)
second                   int      The date's second of the minute (0 - 59)

DateInfo has the following method:

Name                     Type     Comment
======================== ======== ============================================
format(String format)    String   See java.text.SimpleDateFormat format options

