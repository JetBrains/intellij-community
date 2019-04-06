# ChangeReminder

## Decision Function
### Problem
For current commit, we should predict files which might be forgotten to commit or to modify.
### Current solution
#### Structure
Timeline: *commitB* -> *commitA* -> *current commit*

Commits, where was no files from current commit, are not interesting for us.
* *current commit* has *file A*, *file B*, *file C*
* *commit A* has *file A*, *file B*, *file D*
* *commit B* has *file A*, *file D*, *file F* 

Current context from the side of files from current commit:
*file A* was in *commit A*, *commit B*
*file B* was in *commit A*

*file C* wasn’t committed yet.

### The main idea of the solution
We use a sort of Bayes Estimator formula for votings:

*W = v\*R/(m + v) + m\*C/(m + v)*, where:

* *W* — final score
* *v* — the number of votes for the file
* *m* — minimum votes required = 3.2
* *R* — average file’s score
* *С* — average score for all files = 0.25

#### How does voting work
We take a file from current commit and get *k*(20) last commits where it was.
For example, we take *file A*. And get commits: *commit A*, *commit B*.
After that, each of these commits votes for the files contains in it (excluding files from current commit)
In our example *commit A* votes for *file D*, and *commit B* votes for *file D*, *file F*.


The vote is calculating by the formula:

*min(1.0, commit_size / size)*, where
* *commits_size* - the maximum number of files in the voting commit, needed to get the largest voice = 8.0
* *size* - number of files in the voting commit

### Result
Thus, the votes for the files will be as follows:

*(m = 1.6, C = 0.25, commit size = 8.0)*

1. from *file A*:
   * *file D* = [1.0, 1.0]
   * *file F* = [1.0]
   
     Their votes:
        * *file D* = 0.67
        * *file F* = 0.54

2. from *file B*:
   * *file D* = [1.0]
   * *file F* = []
     
     Their votes:
        * *file D* = 0.54
        * *file F* = 0.0

Scores from different files are added so the final scores:

* *file D* = 1.21
* *file F* = 0.54

Normalize them:

* *file D* = 0.4
* *file F* = 0.18

The file will be predicted if its' score is greater than *minProb* (counting personally).