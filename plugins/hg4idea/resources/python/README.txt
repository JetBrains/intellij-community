This Mercurial extension is meant to provide robust integration with
external tools.

Mercurial sometimes asks vital questions during several operations, for
instance during merging. Automatically choosing the default answer
for these questions is typically not what you want when working from,
for instance, an IDE. This extension forwards the prompts to another
process (i.e. the IDE) so that it can be answered by the user.