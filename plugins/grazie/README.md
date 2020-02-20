<h1> <img align="left" width="40" height="40" src="https://plugins.jetbrains.com/files/12175/63853/icon/pluginIcon.svg" alt="Grazi Icon"> Grazie </h1>

[![JetBrains Plugin](https://img.shields.io/jetbrains/plugin/v/12175-grazi.svg?style=flat-square&label=jetbrains%20plugin)](https://plugins.jetbrains.com/plugin/12175-grazi)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/12175-graz.svg?style=flat-square)](https://plugins.jetbrains.com/plugin/12175-grazi)


Grazie is an IntelliJ IDEA plugin providing local spell and grammar checking for Markdown, JavaDoc, Plain texts, and others.

It uses one of the leading proofreaders - [LanguageTool](https://github.com/languagetool-org/languagetool)
under the hood, so it supports over 15 languages and provides the best performance and 
accuracy among free (and even non-free) alternatives.

<p align="center">
  <img src="https://plugins.jetbrains.com/files/12175/screenshot_20233.png" width="75%" />
</p>

## What's inside

Grazie consists of two parts - grammar checker, backed by LanguageTool, and spellchecker backed by LanguageTool dictionaries and IDEA built-in spellcheck. When it is sensible, Grazie will use full checking pipeline (proofreading and spellchecking), but in some cases (e.g., in code) it will use just spellcheck.

Right now Grazie supports following natural language sources:
* Java code - string literals, javadocs and language constructs (methods names etc.)
* Kotlin code - string literals, kdoc and language constructs
* Python code - string literals (formatted and non-formatted), python docs and language constructs
* JavaScript code - string literals, docs and language constructs
* Rust code - string literals, docs and language constructs
* Latex - (via TeXiFy IDEA plugin) text, spellcheck
* Markdown - all the text (for code Grazie will use spellcheck only)
* Plaintext - all the text if extension is *.txt*, otherwise Grazie will use spellcheck only
* XML - all the text elements
* JSON - string literals
* Properties - string literals
* Comments - any comments in almost any code 
* Commit messages - commits made via standard IDEA VCS support

As for languages, Grazie supports (including dialects):
* English (British, American, Canadian)
* Russian
* Persian
* French
* German (Germany, Austrian)
* Polish
* Italian
* Dutch
* Portuguese (Brazilian, Portugal)
* Chinese
* Greek
* Japanese
* Romanian
* Slovak
* Spanish
* Ukrainian

Moreover, Grazie supports *native language based* inspections. It means that if you specify your native language, Grazie will provide you with additional inspections for the language you are writing on.

## Dev versions

You can subscribe to development channel of Grazie plugin to get the latest updates (maybe a bit unstable) automatically.

Just add `dev` channel (https://plugins.jetbrains.com/plugins/dev/list) in accordance with [documentation](https://www.jetbrains.com/help/idea/managing-plugins.html)

## Setup

For local development and testing Gradle is used:

* Import project as a Gradle project with IntelliJ IDEA (version `2018.3.+`)
* Run `runIde` task to run IntelliJ IDEA Community (downloaded by Gradle) 
  with installed Grazie plugin from current build
  
## Special thanks
Special thanks goes to:
* Alexandra Pavlova (aka sunalex) for our beautiful icon
* Alexandr Sadovnikov and Nikita Sokolov as one of initial developers of plugin


